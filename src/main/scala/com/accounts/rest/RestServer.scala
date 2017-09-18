package com.accounts.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.common.{EntityStreamingSupport, JsonEntityStreamingSupport}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.persistence.inmemory.query.scaladsl.InMemoryReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.accounts.rest.actor.Account.{Deposited, Initialized, Withdrawn}
import com.accounts.rest.actor.Bank
import com.accounts.rest.actor.Bank.{AccountNotExist, Transfer}
import com.accounts.rest.actor.Transaction.{MoneyDeposited, MoneyNotEnough, Rollback}
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object RestServer extends HttpApp {

  case class Account(accountId: Long, balance: Long)
  case class CreateTransfer(amount: Long, from: Long, to: Long)

  case class Response(message: String)


  implicit val accountFormat = jsonFormat2(Account)
  implicit val createTransferFormat = jsonFormat3(CreateTransfer)
  implicit val responseFormat = jsonFormat1(Response)

  implicit val jsonStreamingSupport: JsonEntityStreamingSupport =
    EntityStreamingSupport.json()
      .withParallelMarshalling(parallelism = 8, unordered = false)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val executionContext = system.dispatcher
  implicit val timeout: Timeout = 10.seconds

  val accounts = Map(
    1L -> 100L,
    2L -> 90L,
    3L -> 110L,
    4L -> 200L,
    5L -> 600L
  )

  val bank = system.actorOf(Bank.props(accounts), "bank")

  def main(args: Array[String]): Unit = {
    RestServer.startServer("localhost", 8080, system)
  }

  def accountsState() = {
    val readJournal: InMemoryReadJournal = PersistenceQuery(system)
      .readJournalFor[InMemoryReadJournal](InMemoryReadJournal.Identifier)

    readJournal.currentPersistenceIds()
      .filter(_.startsWith("account-"))
      .flatMapConcat { accountId =>
        readJournal.currentEventsByPersistenceId(accountId, 0, Long.MaxValue)
          .fold(0L)((balance, elem) => elem.event match {
            case Initialized(amount) => amount
            case Deposited(amount, _) => balance + amount
            case Withdrawn(amount, _) => balance - amount
          })
          .map {
            Account(accountId.replace("account-", "").toLong, _)
          }
      }
  }

  override def routes:Route =
    path("accounts") {
      get {
        complete(
          accountsState())
      }
    } ~
      post {
        path("payment" / "create") {
          entity(as[CreateTransfer]) { createTransfer =>
            val transfer = Transfer(createTransfer.amount, createTransfer.from, createTransfer.to)
            val answer = (bank ? transfer)
            onComplete(answer) {
              case Failure(ex) => complete(StatusCodes.BadRequest, Response(ex.toString))
              case Success(r) => r match {
                case MoneyDeposited => complete(Response("Ok"))
                case Rollback => complete(StatusCodes.BadRequest, Response("Not possible to process this payment"))
                case MoneyNotEnough => complete(StatusCodes.BadRequest, Response("Not enough money on source account"))
                case AccountNotExist => complete(StatusCodes.BadRequest, Response("Account does not exist"))
              }
            }
          }
        }
      }

}
