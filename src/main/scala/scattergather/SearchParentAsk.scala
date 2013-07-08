package scattergather

import collection.immutable.HashMap
import akka.actor.{ReceiveTimeout, ActorRef, Actor,Props}
import akka.pattern.ask

trait SearchParentAsk { self: AdaptiveSearchNodeAsk =>
 var children = IndexedSeq[ActorRef]()
 var currentIdx = 0
 def parentNode: PartialFunction[Any, Unit] = {
    case GetSearchNodes =>
      sender ! children
    case s @ SearchableDocument(_) => getNextChild ! s
  }

  // Round Robin
  private def getNextChild = {
    currentIdx = (1 + currentIdx) % children.size
    children(currentIdx)
  }

}