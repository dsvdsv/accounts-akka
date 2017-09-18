package com.accounts.rest.actor


import akka.actor.{ActorLogging, ActorRef, Cancellable, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.accounts.rest.actor.Account._

import scala.concurrent.duration._

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
        case _: TransactionStarted =>
          withdrawMoney(started)
        case MoneyWithdrawn =>
          depositMoney(started)
        case MoneyDeposited => context.stop(self)
        case Rollback       => context.stop(self)
        case MoneyNotEnough => context.stop(self)
      }
    }
  }

  override def receiveCommand = {
    case TransferMoney(amount, from, to) =>
      persist(TransactionStarted(amount, from.path.toString, to.path.toString))(withdrawMoney)
  }

  def withdrawMoney(ts: TransactionStarted): Unit = {
    context.actorSelection(ts.fromActor) ! Withdraw(ts.amount, transactionId)

    context.become(
      awaitWithdrawn(
        scheduleRollback,
        ts
      )
    )
  }

  def awaitWithdrawn(rollbackTask: Cancellable, t: TransactionStarted): Receive = {
    case r @ Rollback =>
      persist(r){_ => context.stop(self) }
    case Withdrawn(_, _) =>
      rollbackTask.cancel()
      persist(MoneyWithdrawn)(_ => depositMoney(t))
    case BalanceNotEnough =>
      rollbackTask.cancel()
      persist(MoneyNotEnough){_ => context.stop(self) }
  }

  def depositMoney(ts: TransactionStarted): Unit = {
    context.actorSelection(ts.toActor) ! Deposit(ts.amount, transactionId)

    context.become(
      awaitDeposited(
        scheduleRollback,
        ts
      ))
  }

  def awaitDeposited(rollbackTask: Cancellable, t: TransactionStarted): Receive = {
    case Rollback =>
      context.actorSelection(t.fromActor) ! Deposit(t.amount, transactionId)
      context.become(awaitRollback)
    case Deposited(_, _) =>
      rollbackTask.cancel()
      persist(MoneyDeposited){_ => context.stop(self) }
  }

  def awaitRollback: Receive = {
    case Deposited(_, _) =>
      persist(Rollback) {_ => context.stop(self) }
  }

  private def scheduleRollback = {
    context.system
      .scheduler.scheduleOnce(100 milliseconds, self, Rollback)
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
  case object MoneyNotEnough extends TransactionEvent

  def props(transactionId: Long) =
    Props(new Transaction(transactionId))
}
