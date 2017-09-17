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
        log.debug("Account {} is initialized with balance {}", id, balance)
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
        log.debug("Account {} is deposited, balance {}", id, newBalance)
        sender() ! d
      }
    case Withdraw(amount, transactionId) =>
      if (balance < amount) {
        throw BalanceNotEnough()
      } else {
        persist(Withdrawn(amount, transactionId)) { w =>
          val newBalance = balance - w.amount
          printf("New balance " + newBalance)
          context.become(awaitCommand(newBalance))
          log.debug("Account {} is withdrawn, balance {}", id, newBalance)
          sender() ! w
        }
      }
    case GetBalance => sender() ! Balance(balance)
  }

}

sealed trait AccountCommand
sealed trait AccountEvent

object Account {

  // protocol
  private case class Create(balance: Long) extends AccountCommand
  case class Deposit(amount: Long, transactionId: Long) extends AccountCommand
  case class Withdraw(amount: Long, transactionId: Long) extends AccountCommand
  case object GetBalance extends AccountCommand

  case class Balance(balance: Long)
  case class BalanceNotEnough() extends RuntimeException

  // events
  case class Initialized(amount: Long) extends AccountEvent
  case class Withdrawn(amount: Long, transactionId: Long) extends AccountEvent
  case class Deposited(amount: Long, transactionId: Long) extends AccountEvent

  def props(id: Long, initBalance: Long) =
    Props(new Account(id, initBalance))
}