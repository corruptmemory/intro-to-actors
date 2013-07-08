package scattergather

import collection.immutable.HashMap
import akka.actor.{ReceiveTimeout, ActorRef, Actor,Props}
import scala.language.postfixOps
import akka.pattern.ask

class AdaptiveSearchNodeAsk extends Actor with SearchParentAsk with SearchLeafAsk {
  // Start as a Leaf Node.
  def receive = leafNode

  /** Splits this search node into a tree of search nodes if there are too many documents. */
  protected def split(): Unit = {
    children = (for((docs, idx) <- documents grouped 5 zipWithIndex) yield {
      val child = context.actorOf(Props(new AdaptiveSearchNodeAsk).withDispatcher("search-tree-dispatcher"),
                                  name = "search-node-ask-"+ idx)
      docs foreach (child ! SearchableDocument(_))
      child
    }).toIndexedSeq
    clearIndex()
    context become parentNode
  }

}