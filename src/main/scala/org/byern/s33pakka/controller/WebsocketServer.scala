package org.byern.s33pakka.controller

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.byern.s33pakka.core
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.session.ConnectedUser

object WebsocketServer {
  def props(sessionManager: ActorRef): Props = Props(new WebsocketServer(sessionManager))
}

class WebsocketServer(sessionManager: ActorRef) extends Actor with ActorLogging with Directives {
  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  implicit val materializer = ActorMaterializer()
  implicit val system = context.system

  override def preStart(): Unit = {
    def newUser(): Flow[Message, Message, NotUsed] = {
      val connectedWsActor = context.system.actorOf(ConnectedUser.props(sessionManager))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          case TextMessage.Strict(text) =>
            try {
              mapper.readValue(text, classOf[ClientMessage])
            }
            catch {
              case e: Exception =>
                log.debug("Bad Message! ", e)
                core.Message.Null()
            }
        }.to(Sink.actorRef[core.Message](connectedWsActor, PoisonPill))

      val outgoingMessages: Source[Message, NotUsed] =
        Source.actorRef[ClientResponse](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            connectedWsActor ! ConnectedUser.Connected(outActor)
            NotUsed
          }.map(
          (outMsg: ClientResponse) => {
            val responseStr = mapper.writeValueAsString(outMsg)
            log.debug("Out message: " + responseStr)
            TextMessage(responseStr)
          }
        )

      Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
    }

    val route =
      path("example") {
        get {
          handleWebSocketMessages(newUser())
        }
      }

    Http().bindAndHandle(route, "127.0.0.1", 8181)
  }

  override def receive: Receive = {
    case core.Message => println("a")
  }
}
