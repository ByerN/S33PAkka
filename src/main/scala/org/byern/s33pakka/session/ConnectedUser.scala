package org.byern.s33pakka.session

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.session.ConnectedUser.Connected

object ConnectedUser {
  case class Connected(outgoing: ActorRef)
  def props(sessionManager: ActorRef) = Props(new ConnectedUser(sessionManager))
}

class ConnectedUser(sessionManager: ActorRef) extends Actor with ActorLogging {
  override def receive: Receive = waiting

  def waiting: Receive = {
    case Connected(wsActor) =>
      log.info(s"WS user: $wsActor has connected")
      context become connected(wsActor)
  }

  def connected(wsUser: ActorRef): Receive = {
    case msg : ClientMessage =>
      sessionManager ! msg
    case msg : ClientResponse =>
      wsUser ! msg
    case _ =>
      log.debug("Unknown message")
  }
}

