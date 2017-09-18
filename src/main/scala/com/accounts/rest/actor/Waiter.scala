package com.accounts.rest.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern.pipe
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

class Waiter(transactionId: Long, transaction: ActorRef, replayTo: ActorRef) extends Actor
  with ActorLogging {

  implicit val materializer = ActorMaterializer()

  import context.dispatcher

  override def preStart(): Unit = {
    super.preStart()
    context.watch(transaction)
  }

  override def receive: Receive = {
    case Terminated(_) =>
      val readJournal: InMemoryReadJournal = PersistenceQuery(context.system)
        .readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

      val lastElement = readJournal
        .currentEventsByPersistenceId(s"transaction-$transactionId", 0, Long.MaxValue)
        .map(_.event)
        .runWith(Sink.last)

      lastElement.pipeTo(replayTo)
        .onComplete { _ => materializer.shutdown() }
      println(s"Transaction completed $transactionId")
      context.stop(self)
  }
}

object Waiter {
  def props(transactionId: Long, transaction: ActorRef, replayTo: ActorRef) =
    Props(new Waiter(transactionId, transaction, replayTo))
}
