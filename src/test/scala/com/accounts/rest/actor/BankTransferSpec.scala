package com.accounts.rest.actor

import akka.actor.ActorSystem
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.accounts.rest.actor.Account.{Deposited, Initialized, Withdrawn}
import com.accounts.rest.actor.Bank.{FromAccountNotExist, ToAccountNotExist, Transfer}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class BankTransferSpec extends TestKit(ActorSystem("BankTransferSpec", config)) with ImplicitSender
  with FunSpecLike with Matchers with BeforeAndAfterAll {
  val bank = system.actorOf(Bank.props(Map(1L -> 100L, 2L -> 90L, 3L -> 110L)), "bank")

  implicit val mat = ActorMaterializer()(system)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  describe("An Bank actor") {

    it("success flow can be work") {
      bank ! Transfer(10, 1, 2)

      Thread.sleep(2000)

      val readJournal: InMemoryReadJournal = PersistenceQuery(system)
        .readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

      val accounts = readJournal.currentPersistenceIds()
        .filter(_.startsWith("account-"))
        .flatMapConcat { accountId =>
          readJournal.currentEventsByPersistenceId(accountId, 0, Long.MaxValue)
            .fold(0L)((balance, elem) => elem.event match {
              case Initialized(amount) => amount
              case Deposited(amount, _) => balance + amount
              case Withdrawn(amount, _) => balance - amount
            }
            ).map { b =>
              (accountId, b)
          }
        }
        .runWith(Sink.seq)

      val a = Await.result(accounts, 7.second)
      println(a)
    }

    it("get BalanceNotEnough if impossible to withdraw") {
      bank ! Transfer(110, 1, 2)
    }

    it("get FromAccountNotExist if both account not exist") {
      val actorRef = TestActorRef(new Bank(Map.empty))

      intercept[FromAccountNotExist] {
        actorRef.receive(Transfer(10, 1, 2))
      }
    }

    it("get FromAccountNotExist if from account not exist") {
      val actorRef = TestActorRef(new Bank(Map(2L -> 10L)))

      intercept[FromAccountNotExist] {
        actorRef.receive(Transfer(10, 1, 2))
      }
    }

    it("get ToAccountNotExist if to account not exist") {
      val actorRef = TestActorRef(new Bank(Map(2L -> 10L)))

      intercept[ToAccountNotExist] {
        actorRef.receive(Transfer(10, 2, 1))
      }
    }
  }
}
