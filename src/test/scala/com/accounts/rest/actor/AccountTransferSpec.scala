package com.accounts.rest.actor

import akka.actor.ActorSystem
import akka.testkit.{EventFilter, ImplicitSender, TestKit, TestProbe}
import com.accounts.rest.actor.Account._
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.duration._

class AccountTransferSpec extends TestKit(ActorSystem("AccountTransferSpec")) with ImplicitSender
  with FunSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  describe("An Account actor") {

    it("account should success deposited") {
      val probe = TestProbe()

      info("Create account with balance = 100")
      val account = system.actorOf(Account.props(1, 100))

      info("Deposit 10 to account")
      (account ! Deposit(10, 1)) (probe.ref)

      probe.expectMsg(1.second, Deposited(10, 1))
    }

    it("account should success withdrawn") {
      val probe = TestProbe()

      info("Create account with balance = 100")
      val account = system.actorOf(Account.props(2, 100))

      info("Withdraw 10 from account")
      (account ! Withdraw(10, 1)) (probe.ref)

      probe.expectMsg(1.second, Withdrawn(10, 1))
    }

    it("get error if balance not enough") {

      val probe = TestProbe()

      val account = system.actorOf(Account.props(3, 10))

      (account ! Withdraw(100, 1)) (probe.ref)

      probe.expectMsg(1.second, BalanceNotEnough)
    }
  }
}
