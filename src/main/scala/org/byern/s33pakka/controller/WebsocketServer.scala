package org.byern.s33pakka.controller

import akka.NotUsed
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import org.byern.s33pakka.MainApp.{get, handleWebSocketMessages, mapper, path}
import org.byern.s33pakka.core
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.session.ConnectedUser

object WebsocketServer {
  def props(sessionManager: ActorRef): Props = Props(new WebsocketServer(sessionManager))
}

class WebsocketServer(sessionManager: ActorRef) extends Actor {
  implicit val materializer = ActorMaterializer()
  implicit val system = context.system

  override def preStart(): Unit = {
    def newUser(): Flow[Message, Message, NotUsed] = {
      val connectedWsActor = context.system.actorOf(ConnectedUser.props(sessionManager))

      val incomingMessages: Sink[Message, NotUsed] =
        Flow[Message].map {
          case TextMessage.Strict(text) =>
            println(text)
            try {
              mapper.readValue(text, classOf[ClientMessage]) match {
                case message: core.Message =>
                  println(message)
                  message
              }
            }
            catch {
              case e: Exception =>
                println("Bad message!")
                println(e)
                core.Message.Null()
            }
        }.to(Sink.actorRef[core.Message](connectedWsActor, PoisonPill))

      val outgoingMessages: Source[Message, NotUsed] =
        Source.actorRef[ClientResponse](10, OverflowStrategy.fail)
          .mapMaterializedValue { outActor =>
            // give the user actor a way to send messages out
            connectedWsActor ! ConnectedUser.Connected(outActor)
            NotUsed
          }.map(
          // transform domain message to web socket message

          (outMsg: ClientResponse) => {
            println("outL: " + mapper.writeValueAsString(outMsg))
            TextMessage(mapper.writeValueAsString(outMsg))
          }
        )

      // then combine both to a flow
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

  override def receive: Receive =  {
    case core.Message => println("a")
  }
}
