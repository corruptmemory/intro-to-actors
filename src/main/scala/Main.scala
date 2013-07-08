import scattergather._
import frontend._
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.dispatch.Dispatchers
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import com.typesafe.config.{ConfigFactory, Config}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._

class Client(frontEnd:ActorRef) extends Actor {
  var originalSender:ActorRef = _
  def receive = {
    case q:frontend.SearchQuery =>
      originalSender = sender
      frontEnd ! q
    case r@QueryResponse(_, false) =>
      originalSender ! r
    case r@QueryResponse(_, true) =>
      originalSender ! r
  }
}

object AdaptiveSearchTreeMain {

  def loadConfig: Config = ConfigFactory.load()
  lazy val system = ActorSystem.create("search-example", loadConfig)

  def submitInitialDocuments(searchNode: ActorRef) =
    Seq("Some example data for you",
        "Some more example data for you to use",
        "To be or not to be, that is the question",
        "OMG it's a cat",
        "This is an example.  It's a great one",
        "HAI there",
        "HAI IZ HUNGRY",
        "Hello, World",
        "Hello, and welcome to the search node 8",
        "The lazy brown fox jumped over the",
        "Winning is the best because it's winning."
    ) foreach (doc =>  searchNode ! SearchableDocument(doc))


  // The root of the actor tree that implements search
  lazy val tree = {
    val searchTree =
      system.actorOf(Props(new AdaptiveSearchNode).withDispatcher("search-tree-dispatcher"),
                     "search-tree")
    submitInitialDocuments(searchTree)
    searchTree
  }


  // Caches query results
  lazy val cache = {
    system.actorOf(
        Props(new SearchCache(tree,"search-cache-dispatcher"))
        .withDispatcher("search-cache-dispatcher"), "search-cache")
  }


  // Provides a throttle/circuit-breaker for the search system
  lazy val throttle = {
    system.actorOf(
        Props(new FrontEndThrottler(cache,"front-end-dispatcher"))
        .withDispatcher("front-end-dispatcher"), "throttler")
  }


  // "front-end" of the search system
  // Stack: fe -> throttler -> cache -> search tree
  lazy val fe = {
    system.actorOf(
        Props(new FrontEnd(throttle))
        .withDispatcher("front-end-dispatcher"), "front-end")
  }


  // Actor that receives the final query result
  lazy val client = {
    system.actorOf(
        Props(new Client(fe)), "client")
  }

  def search(in:String,maxDocs:Int):Future[QueryResponse] = {
    implicit val timeout = new Timeout(10.seconds)

    (client ask frontend.SearchQuery(in,maxDocs)).mapTo[QueryResponse]
  }

  // Main entry point for demo
  def forcedSearch(in:String,maxDocs:Int):QueryResponse =
    Await.result(search(in,maxDocs),10.seconds)


  def shutdown(): Unit = system.shutdown()
}