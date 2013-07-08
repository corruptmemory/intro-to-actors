package frontend

import akka.actor.{Actor,ActorRef, ReceiveTimeout, Props}
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scattergather.QueryResponse
import scala.language.postfixOps

// Disables queries from going through if more than
class FrontEndThrottlerAsk(tree: ActorRef, dispatcher:String) extends Actor {
  // Value used to determine if circuit-breaker should be used
  var badQueryCount: Int = 0

  // Timeout query if executing time longer than this amount
  val currentTimeout: Duration = 1 seconds

  def receive: Receive = {
    case q: SearchQuery =>
      issueQuery(scattergather.SearchQueryAsk(q.query, q.maxDocs),sender)
    case q: scattergather.SearchQueryAsk =>
      issueQuery(q,sender)
    case TimedOut =>
      Console.println("Query timeout!")
      badQueryCount += 1
    case Success =>
      Console.println("Query Success!")
      if(badQueryCount > 0) badQueryCount -= 1
  }

  def issueQuery(q: scattergather.SearchQueryAsk,sender: ActorRef): Unit =
    if(badQueryCount > 10) {
      Console.println("Fail whale!")
      // Slowly lower count until we're back to normal and allow some through.
      badQueryCount -= 1
      sender ! Defaults.badQueryResponse
    }
    else {
      val timeout = currentTimeout
      val timer =
        // One-off timer for responses
        context.actorOf(Props(new QueryTimer(timeout,
                                             sender,
                                             self,
                                             Defaults.badQueryResponse)).withDispatcher(dispatcher))
      tree ! scattergather.SearchQuery(q.query, q.maxDocs, timer)
    }
}
