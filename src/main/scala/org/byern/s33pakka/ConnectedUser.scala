package org.byern.s33pakka
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.byern.s33pakka.ConnectedUser.Connected
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}

class ConnectedUser(sessionManager: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = waiting

  def waiting: Receive = {
    // When the user connects, tell the chat room about it so messages
    // sent to the chat room are routed here
    case Connected(wsActor) =>
      log.info(s"WS user: $wsActor has connected")
      context become connected(wsActor)
  }

  def connected(wsUser: ActorRef): Receive = {
    // any messages coming from the WS client will come here and will be sent to the chat room
    case msg : ClientMessage =>
        log.debug("Intermediate Actor sending message to chat room")
      sessionManager ! msg
    case msg : ClientResponse =>
      log.debug("response ")
      wsUser ! msg
    case _ =>
      log.debug("some shit")
  }
}

object ConnectedUser {
  sealed trait UserMessage
  case class Connected(outgoing: ActorRef)
  case class IncomingMessage(text: String) extends UserMessage
  case class OutgoingMessage(text: String) extends UserMessage

  def props(chatRoom: ActorRef) = Props(new ConnectedUser(chatRoom))
}