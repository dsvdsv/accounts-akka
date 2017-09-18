package com.accounts.rest


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}
import spray.json.{JsArray, JsNumber, JsObject, JsonParser}

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
        1.second
      )

      response.status shouldEqual (StatusCodes.OK)

      val body = Await.result(
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String),
        1.second
      )

      JsonParser.apply(body) shouldEqual(
        JsArray(
          JsObject(Map("accountId" -> JsNumber(1), "balance" -> JsNumber(100))),
          JsObject(Map("accountId" -> JsNumber(2), "balance" -> JsNumber(90))),
          JsObject(Map("accountId" -> JsNumber(3), "balance" -> JsNumber(110))),
          JsObject(Map("accountId" -> JsNumber(4), "balance" -> JsNumber(200))),
          JsObject(Map("accountId" -> JsNumber(5), "balance" -> JsNumber(600)))
        )
        )
    }
  }
}
