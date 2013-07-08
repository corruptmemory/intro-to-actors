package scattergather

import collection.immutable.HashMap
import akka.actor.{ReceiveTimeout, ActorRef, Actor,Props}
import scala.language.postfixOps

class AdaptiveSearchNode extends Actor with SearchParent with SearchLeaf {
  // Start as a Leaf Node.
  def receive = leafNode

  /** Splits this search node into a tree of search nodes if there are too many documents. */
  protected def split(): Unit = {
    // Create the set of 'children' search "leaf" nodes
    children = (for((docs, idx) <- documents grouped 5 zipWithIndex) yield {

      // Create child "search leaf" node -- represents 1/5 of the total search index
      val child = context.actorOf(Props(new AdaptiveSearchNode).withDispatcher("search-tree-dispatcher"),
                                        name = "search-node-"+ idx)

      // Send the document for indexing to the child
      docs foreach (child ! SearchableDocument(_))
      child
    }).toIndexedSeq
    clearIndex()
    // Switch to the "parentNode" logic
    context become parentNode
  }

}