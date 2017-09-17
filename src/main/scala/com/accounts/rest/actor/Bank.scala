package com.accounts.rest.actor

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.pattern.{ ask, pipe }
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.accounts.rest.actor.Account.{Deposited, GetBalance, Initialized, Withdrawn}
import com.accounts.rest.actor.Bank.{FromAccountNotExist, GetAccountsState, ToAccountNotExist, Transfer}
import com.accounts.rest.actor.Transaction.TransferMoney

class Bank(accounts: Map[Long, Long]) extends Actor
  with Stash with ActorLogging {

  override def preStart(): Unit = {
    super.preStart()
    accounts.foreach {
      case (accountId, initBalance) =>
        val name =  accountName(accountId)
        context.actorOf(
          Account.props(accountId, initBalance),
          name
        )
        log.debug("Account {} with balance {} created", name, initBalance)
    }
  }

  override def receive = {
    case GetAccountsState =>


    case Transfer(amount, from, to) =>
      (context.child(accountName(from)), context.child(accountName(to))) match {
        case (Some(fromAccount), Some(toAccount)) =>
          val transaction = context.actorOf(Transaction.props(System.currentTimeMillis()))
          transaction ! TransferMoney(amount, fromAccount, toAccount)
        case (None, _) => throw FromAccountNotExist(from)
        case _ => throw ToAccountNotExist(to)
      }

  }

  def accountName(accountId: Long) = s"account-$accountId"
}

sealed trait BankCommand

object Bank {
  // protocol
  case class Transfer(amount: Long, from: Long, to: Long) extends BankCommand
  case object GetAccountsState extends BankCommand

  case class ToAccountNotExist(accountId: Long) extends RuntimeException
  case class FromAccountNotExist(accountId: Long) extends RuntimeException

  case class Accounts(accounts: Map[Long, Long])

  def props(accounts: Map[Long, Long]) =
    Props(new Bank(accounts))
}
