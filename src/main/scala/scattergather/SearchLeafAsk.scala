package scattergather

import collection.immutable.HashMap
import akka.actor.{ReceiveTimeout, ActorRef, Actor,Props}

// Ask-based version of SearchLeaf
// Sends result back to sender
trait SearchLeafAsk { self: AdaptiveSearchNodeAsk =>
  final val maxNoOfDocuments = 10
  var documents: Vector[String] = Vector()
  var index: HashMap[String, Seq[(Double, String)]] = HashMap()


  def leafNode: PartialFunction[Any, Unit] = {
    // hacks to excercise behavior
    // Notice that response sent back to sender
    case SearchQueryAsk("BAD", _) => sender ! QueryResponse(Seq.empty, failed=true)

    // Notice that response sent back to sender
    case SearchQueryAsk(query, maxDocs) => sender ! executeLocalQuery(query, maxDocs)
    case SearchableDocument(content)          => addDocumentToLocalIndex(content)
  }


  private def executeLocalQuery(query: String, maxDocs: Int):QueryResponse = {
    val result = for {
      results <- (index get query).toSeq
      resultList <- results
    } yield resultList

    QueryResponse(result take maxDocs, failed=false)
  }

  private def addDocumentToLocalIndex(content: String) = {
    documents = documents :+ content
    // Split on size or add to index.
    if (documents.size > maxNoOfDocuments) split()
    else {
      for( (key,value) <- content.split("\\s+").groupBy(identity)) {
        val list = index.get(key) getOrElse Seq()
        index += ((key, ((value.length.toDouble, content)) +: list))
      }
    }
  }

  /** Abstract method to split this actor. */
  protected def split(): Unit

  protected def clearIndex(): Unit = {
    documents = Vector()
    index = HashMap()
  }
}