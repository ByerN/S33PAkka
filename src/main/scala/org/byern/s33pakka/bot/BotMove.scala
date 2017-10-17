package org.byern.s33pakka.bot

import akka.actor.{Actor, ActorRef, Props}

object BotMove {
  def props(sessionManager: ActorRef, id: String): Props =
    Props(new BotMove(id, sessionManager))
}

class BotMove(id: String, sessionManager: ActorRef) extends Actor {

  override def receive = {
    case _ =>

  }
}
