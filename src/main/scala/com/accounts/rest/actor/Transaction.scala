package com.accounts.rest.actor


import akka.actor.{ActorLogging, ActorRef, Cancellable, Props}

import scala.concurrent.duration._
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.accounts.rest.actor.Account.{Deposited, Deposit, Withdraw, Withdrawn}

class Transaction(transactionId: Long) extends PersistentActor
  with ActorLogging {

  import Transaction._
  import context.dispatcher

  override def persistenceId = s"transaction-$transactionId"

  override def receiveRecover = {
    var started: TransactionStarted = null
    var last: TransactionEvent = null

    {
      case t: TransactionStarted =>
        started = t
        last = t
      case e: TransactionEvent =>
        last = e
      case RecoveryCompleted => last match {
        case null => // wait for initialization
        case t: TransactionStarted =>
          withdrawMoney(started)
        case MoneyWithdrawn =>
          depositMoney(started)
        case MoneyDeposited => context.stop(self)
        case Rollback       => context.stop(self)
      }
    }
  }

  override def receiveCommand = {
    case TransferMoney(amount, from, to) =>
      persist(TransactionStarted(amount, from.path.toString, to.path.toString))(withdrawMoney)
  }

  def withdrawMoney(t: TransactionStarted): Unit = {
    context.actorSelection(t.fromActor) ! Withdraw(t.amount, transactionId)
    val cancelTask = context.system
      .scheduler.scheduleOnce(100 milliseconds, self, Rollback)

    context.become(awaitWithdrawn(cancelTask, t))
  }

  def awaitWithdrawn(cancelTask: Cancellable, t: TransactionStarted): Receive = {
    case r @ Rollback =>
      persist(r)(_ => context.stop(self))
    case Withdrawn(_, _) =>
      cancelTask.cancel()
      persist(MoneyWithdrawn)(_ => depositMoney(t))
  }

  def depositMoney(t: TransactionStarted): Unit = {
    context.actorSelection(t.toActor) ! Deposit(t.amount, transactionId)
    val cancelTask = context.system
      .scheduler.scheduleOnce(100 milliseconds, self, Rollback)
    context.become(awaitDeposited(cancelTask, t))
  }

  def awaitDeposited(cancelTask: Cancellable, t: TransactionStarted): Receive = {
    case r @ Rollback =>
      context.actorSelection(t.fromActor) ! Deposit(t.amount, transactionId)
      context.become(awaitRollback)
    case Deposited(_, _) =>
      cancelTask.cancel()
      persist(MoneyDeposited)(_ => context.stop(self))
  }

  def awaitRollback: Receive = {
    case Deposited(_, _) =>
      persist(Rollback)(_ => context.stop(self))
  }
}

sealed trait TransactionCommand
sealed trait TransactionEvent

object Transaction{
  // protocol
  case class TransferMoney(amount: Long, from: ActorRef, to: ActorRef)
    extends TransactionCommand

  // events
  case class TransactionStarted(
     amount: Long,
     fromActor: String,
     toActor: String
  ) extends TransactionEvent

  case object MoneyWithdrawn extends TransactionEvent
  case object MoneyDeposited extends TransactionEvent
  case object Rollback extends TransactionEvent


  def props(transactionId: Long) =
    Props(new Transaction(transactionId))
}
