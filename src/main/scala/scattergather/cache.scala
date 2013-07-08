package scattergather

import akka.actor.{ReceiveTimeout, ActorRef, Actor, Props}
import scala.concurrent.duration._

// Some actor messages
case class AddToCache(query: String, r: QueryResponse)
case class Invalidate(query: String)

// Provides a cache for search queries.
class SearchCache(index: ActorRef, dispatcher:String) extends Actor {
  // Our low-budget cache
  var cache: Map[String, QueryResponse] = Map.empty

  def receive: Receive = {
    // Hack for behavior...
    case SearchQuery("DROP", _, _) => //Ignore for timeout...
    case q: SearchQuery => issueSearch(q)
    // Idempotent operation -- important
    // May get 1 or more 'AddToCache' for same 'q'
    case AddToCache(q, r) =>
      println("Caching ["+q+"]!")
      cache += q -> r
    case Invalidate(q) =>
      cache -= q
  }

  def issueSearch(q: SearchQuery): Unit = {
    // See if the query is in our cache
    (cache get q.query) match {
        case Some(response) =>
          println("Repsonding ["+q.query+"] with cache!")
          q.gatherer ! response
        case _ =>
          // Adapt the query so we have a chance to add it to our cache.
          // One-off actor to intercept and mark for caching the response
          val int =
            context.actorOf(Props(new CacheInterceptor(self,
                                                       q.gatherer,
                                                       q.query)).withDispatcher(dispatcher))
          index ! SearchQuery(q.query, q.maxDocs, int)
      }
  }
}

// Resource leak: not guaranteed to get a QueryResponse <- failure mode
class CacheInterceptor(cache: ActorRef, listener: ActorRef, query: String) extends Actor {
  def receive: Receive = {
    case q: QueryResponse =>
      listener ! q
      if(!q.failed) cache ! AddToCache(query, q)
      context stop self
  }
}
