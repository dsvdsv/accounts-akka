package com.accounts.rest.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.accounts.rest.actor.Transaction.TransferMoney

class Bank(accounts: Map[Long, Long]) extends Actor
  with ActorLogging {

  import Bank._

  override def preStart(): Unit = {
    super.preStart()
    accounts.foreach {
      case (accountId, initBalance) =>
        val name = accountName(accountId)
        context.actorOf(
          Account.props(accountId, initBalance),
          name
        )
    }
  }

  override def receive = {
    case Transfer(amount, from, to) =>
      if (from == to || amount < 1) {
        sender() ! Restricted
      } else {
        (context.child(accountName(from)), context.child(accountName(to))) match {
          case (Some(fromAccount), Some(toAccount)) =>
            makeTransfer(amount, fromAccount, toAccount)
          case _ => sender() ! AccountNotExist
        }
      }
  }

  def accountName(accountId: Long) = s"account-$accountId"

  def makeTransfer(amount: Long, fromAccount: ActorRef, toAccount: ActorRef) = {
    val transactionId = System.currentTimeMillis()

    val transaction = context.actorOf(Transaction.props(transactionId))
    context.actorOf(Waiter.props(transactionId, transaction, sender()))

    transaction ! TransferMoney(amount, fromAccount, toAccount)
  }
}

sealed trait BankCommand

object Bank {

  case class Transfer(amount: Long, from: Long, to: Long) extends BankCommand
  case object AccountNotExist
  case object Restricted

  def props(accounts: Map[Long, Long]) =
    Props(new Bank(accounts))
}
