package org.byern.s33pakka

import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.persistence.PersistentActor
import org.byern.s33pakka.SessionManager.{SessionCreated, SessionMessage}
import org.byern.s33pakka.core.Message
import org.byern.s33pakka.player.Player._
import org.byern.s33pakka.world.World
import org.byern.s33pakka.world.World.{Creature, GetState, MoveThing}

import scala.collection.mutable

object SessionManager {

  case class SessionMessage(sessionId: UUID, msg: Message)

  case class SessionCreated(sessionId: UUID)

  def props(playerSupervisor: ActorRef,
            world: ActorRef
           ): Props = Props(new SessionManager(playerSupervisor, world))
}

class SessionManager(playerSupervisor: ActorRef,
                     world: ActorRef) extends PersistentActor {

  val sessions: mutable.Map[UUID, String] = mutable.Map()
  val loginRequests: mutable.Map[String, ActorRef] = mutable.Map()

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  override def receiveRecover = {
    case _ =>
  }

  override def receiveCommand = {
    case msg@Register(_, _, _) =>
      playerSupervisor forward msg
    case msg@Login(login: String, _) =>
      loginRequests += login -> sender()
      playerSupervisor ! msg
    case msg@IncorrectPassword(login: String) =>
      loginRequests.get(login).foreach(recipient => loginRequests -= login)
    case msg@CorrectPassword(login: String, sign: String) =>
      loginRequests.get(login).foreach {
        recipient =>
          val sessionId = UUID.randomUUID()
          sessions += sessionId -> login
          recipient ! SessionCreated(sessionId)
          world ! World.AddThing(Creature(sign, login))
          loginRequests -= login
      }
    case SessionMessage(sessionId: UUID, msg: MoveThing) =>
      sessions.get(sessionId).foreach(login => world forward msg.copy(id = login))
    case SessionMessage(sessionId: UUID, msg: GetState) =>
      sessions.get(sessionId).foreach(login => world forward msg)
    case _ =>
  }
}
