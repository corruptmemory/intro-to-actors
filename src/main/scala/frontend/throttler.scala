package frontend

import akka.actor.{Actor,ActorRef, ReceiveTimeout, Props}
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scattergather.QueryResponse
import scala.language.postfixOps

object Defaults {
  val badQueryResponse = QueryResponse(Seq.empty, true)
}

// Some additional messages
case object TimedOut
case object Success

// Disables queries from going through if more than
class FrontEndThrottler(tree: ActorRef, dispatcher:String) extends Actor {
  // Value used to determine if circuit-breaker should be used
  var badQueryCount: Int = 0

  // Timeout query if executing time longer than this amount
  val currentTimeout: Duration = 1 seconds

  def receive: Receive = {
    case q: SearchQuery =>
      issueQuery(scattergather.SearchQuery(q.query, q.maxDocs, sender))
    case q: scattergather.SearchQuery =>
      issueQuery(q)
    case TimedOut =>
      Console.println("Query timeout!")
      badQueryCount += 1
    case Success =>
      Console.println("Query Success!")
      if(badQueryCount > 0) badQueryCount -= 1
  }

  def issueQuery(q: scattergather.SearchQuery): Unit =
    if(badQueryCount > 10) {
      Console.println("Fail whale!")
      // Slowly lower count until we're back to normal and allow some through.
      badQueryCount -= 1
      q.gatherer ! Defaults.badQueryResponse
    }
    else {
      val timeout = currentTimeout
      val timer =
        // One-off timer for responses
        context.actorOf(Props(new QueryTimer(timeout,
                                             q.gatherer,
                                             self,
                                             Defaults.badQueryResponse)).withDispatcher(dispatcher))
      tree ! scattergather.SearchQuery(q.query, q.maxDocs, timer)
    }
}



class QueryTimer(timeout: Duration, to: ActorRef, throttler: ActorRef, default: QueryResponse) extends Actor {

  // Let me know if I've not received a message in this amount of time
  context.setReceiveTimeout(timeout)

  def receive: Receive = {
    case x: scattergather.QueryResponse =>
      throttler ! Success
      to ! x
      context stop self
    case ReceiveTimeout =>
      throttler ! TimedOut
      to ! default
      context stop self
  }
}