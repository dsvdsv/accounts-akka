package com.accounts.rest.actor

import akka.actor.{ActorLogging, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}

class Account(id: Long, initBalance: Long) extends PersistentActor
  with ActorLogging {

  import Account._

  override def persistenceId = s"account-$id"

  override def preStart(): Unit = {
    super.preStart()

    self ! Create(initBalance)
  }

  override def receiveRecover: Receive = {
    var balance: Long = 0

    {
      case Initialized(amount) => balance = amount
      case Deposited(amount, _) => balance = balance + amount
      case Withdrawn(amount, _) => balance = balance - amount
      case RecoveryCompleted =>
        if (balance > 0) {
          context.become(awaitCommand(balance))
        }
    }
  }

  override def receiveCommand = {
    case Create(balance) =>
      persist(Initialized(balance)) { i =>
        context.become(awaitCommand(i.amount))
        unstashAll()
      }
    case _ => stash()
  }

  def awaitCommand(balance: Long): Receive = {
    case Deposit(amount, transactionId) =>
      persist(Deposited(amount, transactionId)) { d =>
        val newBalance = balance + d.amount
        context.become(awaitCommand(newBalance))
        sender() ! d
      }
    case Withdraw(amount, transactionId) =>
      if (balance < amount) {
        sender() ! BalanceNotEnough
      } else {
        persist(Withdrawn(amount, transactionId)) { w =>
          val newBalance = balance - w.amount
          context.become(awaitCommand(newBalance))
          sender() ! w
        }
      }
  }

}

sealed trait AccountCommand
sealed trait AccountEvent

object Account {

  private case class Create(balance: Long) extends AccountCommand
  case class Deposit(amount: Long, transactionId: Long) extends AccountCommand
  case class Withdraw(amount: Long, transactionId: Long) extends AccountCommand

  case object BalanceNotEnough

  case class Initialized(amount: Long) extends AccountEvent
  case class Withdrawn(amount: Long, transactionId: Long) extends AccountEvent
  case class Deposited(amount: Long, transactionId: Long) extends AccountEvent

  def props(id: Long, initBalance: Long) =
    Props(new Account(id, initBalance))
}