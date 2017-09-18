package com.accounts.rest


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsNumber, JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class RestServerSpec extends TestKit(ActorSystem("RestServerSpec"))
  with FunSpecLike with Matchers
  with BeforeAndAfterAll {

  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val host = "localhost"
  val port = 9999
  var server: Future[Unit] = _

  override def beforeAll() = {
    Future {
      RestServer.startServer(host, port)
    }
    Thread.sleep(1000)
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  describe("A RestServer") {
    it("should return account state") {
      val response = Await.result(
        Http().singleRequest(HttpRequest(uri = s"http://$host:$port/accounts")),
        10.second
      )

      response.status shouldEqual (StatusCodes.OK)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        10.second
      )

      body.parseJson.convertTo[List[JsObject]] should contain allOf(
          JsObject(Map("accountId" -> JsNumber(1), "balance" -> JsNumber(100))),
          JsObject(Map("accountId" -> JsNumber(2), "balance" -> JsNumber(90))),
          JsObject(Map("accountId" -> JsNumber(3), "balance" -> JsNumber(110))),
          JsObject(Map("accountId" -> JsNumber(4), "balance" -> JsNumber(200))),
          JsObject(Map("accountId" -> JsNumber(5), "balance" -> JsNumber(600)))
        )
    }
    it("should return error if from account not exist") {
      val response = Await.result(
        Http().singleRequest(
          HttpRequest(
            uri = s"http://$host:$port/payment/create",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, """{"amount": 1, "from": 999, "to": 1}""")
          )
        ),
        10.second
      )

      response.status shouldEqual (StatusCodes.BadRequest)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        10.second
      )

      body.parseJson shouldEqual(
          JsObject(Map("message" -> JsString("Account does not exist")))
        )
    }

    it("should return error if to account not exist") {
      val response = Await.result(
        Http().singleRequest(
          HttpRequest(
            uri = s"http://$host:$port/payment/create",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, """{"amount": 1, "from": 1, "to": 1111}""")
          )
        ),
        10.second
      )

      response.status shouldEqual (StatusCodes.BadRequest)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        10.second
      )

      body.parseJson shouldEqual(
        JsObject(Map("message" -> JsString("Account does not exist")))
        )
    }

    it("should return error if from and to accounts is equals") {
      val response = Await.result(
        Http().singleRequest(
          HttpRequest(
            uri = s"http://$host:$port/payment/create",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, """{"amount": 1, "from": 1, "to": 1}""")
          )
        ),
        10.second
      )

      response.status shouldEqual (StatusCodes.BadRequest)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        10.second
      )

      body.parseJson shouldEqual(
        JsObject(Map("message" -> JsString("This transfer is restricted")))
        )
    }

    it("should return error if wrong amount") {
      val response = Await.result(
        Http().singleRequest(
          HttpRequest(
            uri = s"http://$host:$port/payment/create",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, """{"amount": -1, "from": 1, "to": 2}""")
          )
        ),
        10.second
      )

      response.status shouldEqual (StatusCodes.BadRequest)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        10.second
      )

      body.parseJson shouldEqual(
        JsObject(Map("message" -> JsString("This transfer is restricted")))
        )
    }

    it("should ok if money transferred") {
      val response = Await.result(
        Http().singleRequest(
          HttpRequest(
            uri = s"http://$host:$port/payment/create",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, """{"amount": 10, "from": 1, "to": 2}""")
          )
        ),
        10.second
      )

      response.status shouldEqual (StatusCodes.OK)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        10.second
      )

      body.parseJson shouldEqual(
        JsObject(Map("message" -> JsString("Ok")))
        )
    }
  }
}
