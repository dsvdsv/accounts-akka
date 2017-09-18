package com.accounts.rest.actor

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import com.accounts.rest.actor.Bank.{AccountNotExist, Transfer}
import com.accounts.rest.actor.Transaction.{MoneyDeposited, MoneyNotEnough}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.duration._

class BankTransferSpec extends TestKit(ActorSystem("BankTransferSpec")) with ImplicitSender
  with FunSpecLike with Matchers with BeforeAndAfterAll {

  val bank = system.actorOf(Bank.props(Map(1L -> 100L, 2L -> 90L, 3L -> 110L)), "bank")

  implicit val mat = ActorMaterializer()(system)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  describe("An Bank actor") {

    it("success flow can be work") {
      bank ! Transfer(10, 1, 2)
      expectMsg(10.second, MoneyDeposited)
    }

    it("get MoneyNotEnough if impossible to withdraw") {
      bank ! Transfer(110, 1, 2)
      expectMsg(10.second, MoneyNotEnough)
    }

    it("get AccountNotExist if both account not exist") {
      bank ! Transfer(10, 999, 8989)
      expectMsg(10.second, AccountNotExist)

    }

    it("get AccountNotExist if from account not exist") {
      bank ! Transfer(10, 999, 2)
      expectMsg(10.second, AccountNotExist)
    }

    it("get AccountNotExist if to account not exist") {
      bank ! Transfer(10, 1, 3123)
      expectMsg(10.second, AccountNotExist)
    }
  }
}
