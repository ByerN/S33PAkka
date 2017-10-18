package org.byern.s33pakka

import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.persistence.PersistentActor
import com.fasterxml.jackson.annotation.JsonProperty
import org.byern.s33pakka.SessionManager._
import org.byern.s33pakka.core.Persistable
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.player.Player._
import org.byern.s33pakka.world.World
import org.byern.s33pakka.world.World.{Creature, GetState, MoveThing}

import scala.collection.mutable

object SessionManager {

  case class SessionUser(login: String, changeObserver: ActorRef)
  case class SessionMessage(
                             @JsonProperty("sessionId")
                             sessionId: UUID,
                             @JsonProperty("msg")
                             msg: ClientMessage) extends ClientMessage

  case class SessionCreated(sessionId: UUID) extends ClientResponse("SESSION_CREATED")

  case class SessionAdded(sessionId:UUID, sessionUser: SessionUser) extends Persistable

  def props(playerSupervisor: ActorRef,
            world: ActorRef
           ): Props = Props(new SessionManager(playerSupervisor, world))

  trait BroadcastMessage

}

class SessionManager(playerSupervisor: ActorRef,
                     world: ActorRef) extends PersistentActor {

  val sessions: mutable.Map[UUID, SessionUser] = mutable.Map()
  val loginRequests: mutable.Map[String, ActorRef] = mutable.Map()

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  def updateState(event: Persistable): Unit = event match {
    case SessionAdded(sessionId: UUID, sessionUser : SessionUser) =>
      sessions += sessionId -> sessionUser
  }

  override def receiveRecover = {
    case _ =>
  }

  override def receiveCommand = {
    case msg@Register(_, _, _) =>
      println(msg + " received by session manager ")
      playerSupervisor forward msg
    case msg@Login(login: String, _) =>
      loginRequests += login -> sender()
      playerSupervisor ! msg
    case msg@IncorrectPassword(login: String) =>
      loginRequests.get(login).foreach(_ => loginRequests -= login)
    case msg@CorrectPassword(login: String, sign: String) =>
      loginRequests.get(login).foreach {
        recipient =>
          val sessionId = UUID.randomUUID()
          persist(SessionAdded(sessionId, SessionUser(login, recipient))){ event=>
            updateState(event)
          }
          recipient ! SessionCreated(sessionId)
          world ! World.AddThing(Creature(sign, login))
          loginRequests -= login
      }
    case SessionMessage(sessionId: UUID, msg: MoveThing) =>
      sessions.get(sessionId).foreach(sessionUser => world forward msg.copy(id = sessionUser.login))
    case SessionMessage(sessionId: UUID, msg: GetState) =>
      sessions.get(sessionId).foreach(_ => world forward msg)
    case msg: BroadcastMessage =>
      sessions.values.foreach(session => session.changeObserver ! msg)
    case _ =>
  }
}
