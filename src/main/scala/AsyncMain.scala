import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.dispatch.Dispatchers
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, Config}
import frontend._
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import scala.async.Async._
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scattergather._
import ExecutionContext.Implicits.global

object AdaptiveSearchTreeAsyncMain {

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

  // The root of the actor ASK-based tree that implements search
  lazy val askTree = {
    val searchTree =
      system.actorOf(Props(new AdaptiveSearchNodeAsk).withDispatcher("search-tree-dispatcher"),
                     "search-tree-ask")
    submitInitialDocuments(searchTree)
    searchTree
  }


  // First version
  def search(in:String,maxDocs:Int):Future[Seq[(Double, String)]] = async {
    implicit val timeout = new Timeout(10.seconds)

    def combineResults(current : Seq[(Double, String)], next : Seq[(Double, String)]): Seq[(Double, String)] =
      (current ++ next).view.sortBy(_._1).take(maxDocs).force

    val searchNodes:Seq[ActorRef] = await((askTree ask GetSearchNodes).mapTo[Seq[ActorRef]])
    val searchResults:Seq[QueryResponse] = await(Future.sequence(searchNodes.map { sn =>
      (sn ask SearchQueryAsk(in,maxDocs)).mapTo[QueryResponse]
    } map { f =>
      try {
        async(await(f))
      } catch {
        case e:Exception => Future.successful(QueryResponse(Seq.empty, failed=true))
      }
    }))

    searchResults.view.filter(_.failed == false).map(_.results).foldLeft(Seq.empty[(Double, String)]){ (s,v) => combineResults(s,v)}
  }

  def forcedSearch(in:String,maxDocs:Int):Seq[(Double, String)] =
    Await.result(search(in,maxDocs),10.seconds)

  // -------------------------------------------
















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
        Props(new FrontEndThrottlerAsk(cache,"front-end-dispatcher"))
              .withDispatcher("front-end-dispatcher"), "throttler-ask")
  }

  // Second version
  def searchWithThrottle(in:String,maxDocs:Int):Future[Seq[(Double, String)]] = {
    val root = throttle
    async {
      implicit val timeout = new Timeout(10.seconds)

      await((root ask SearchQueryAsk(in,maxDocs)).mapTo[QueryResponse]).results
    }
  }

  def forcedSearchWithThrottle(in:String,maxDocs:Int):Seq[(Double, String)] =
    Await.result(searchWithThrottle(in,maxDocs),10.seconds)

  def shutdown(): Unit = system.shutdown()
}