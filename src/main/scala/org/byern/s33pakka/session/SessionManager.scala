package org.byern.s33pakka.session

import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.persistence.PersistentActor
import com.fasterxml.jackson.annotation.JsonProperty
import org.byern.s33pakka.core.Persistable
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.player.Player
import org.byern.s33pakka.player.Player._
import org.byern.s33pakka.session.SessionManager._
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

  case class SessionCreated(sessionId: UUID, msgType: String="SESSION_CREATED") extends ClientResponse

  case class SessionAdded(sessionId:UUID, sessionUser: SessionUser) extends Persistable

  def props(playerSupervisor: ActorRef,
            world: ActorRef
           ): Props = Props(new SessionManager(playerSupervisor, world))

  trait BroadcastMessage

}

class SessionManager(playerSupervisor: ActorRef,
                     world: ActorRef) extends PersistentActor {

  var sessions: mutable.Map[UUID, SessionUser] = mutable.Map()
  val loginRequests: mutable.Map[String, ActorRef] = mutable.Map()

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  def updateState(event: Persistable): Unit = event match {
    case SessionAdded(sessionId: UUID, sessionUser : SessionUser) =>
      sessions.retain((_, value) => value.changeObserver != sessionUser.changeObserver)
      sessions += sessionId -> sessionUser
  }

  override def receiveRecover = {
    case evt: Persistable =>
      updateState(evt)
  }

  override def receiveCommand = {
    case msg@Register(login:String, _, _, _) =>
      println(msg + " received by session manager ")
      playerSupervisor forward msg.copy(entityId = login)
    case msg@Login(login: String, _, _) =>
      loginRequests += login -> sender()
      playerSupervisor ! msg.copy(entityId = login)
    case msg@IncorrectPassword(login: String, _) =>
      loginRequests.get(login).foreach(loginRequest => loginRequest ! msg)
      loginRequests.retain((key, _) => key != login)
    case msg@Player.NotExists(login:String, _) =>
      loginRequests.get(login).foreach(loginRequest => loginRequest ! msg)
      loginRequests.retain((key, _) => key != login)
    case msg@CorrectPassword(login: String, sign: String) =>
      loginRequests.get(login).foreach {
        recipient =>
          val sessionId = UUID.randomUUID()
          persist(SessionAdded(sessionId, SessionUser(login, recipient))){ event=>
            updateState(event)
          }
          recipient ! SessionCreated(sessionId)
          world ! World.AddThing(Creature(sign, login))
      }
      loginRequests.retain((key, _) => key != login)
    case SessionMessage(sessionId: UUID, msg: MoveThing) =>
      val sessionUser:Option[SessionUser] = sessions.get(sessionId)
      if(sessionUser.isDefined && sessionUser.get.changeObserver != sender()){
        sessions.put(sessionId, sessionUser.get.copy(changeObserver = sender()))
      }
      sessions.get(sessionId).foreach(sessionUser => world forward msg.copy(id = sessionUser.login))
    case SessionMessage(sessionId: UUID, msg: GetState) =>
      val sessionUser:Option[SessionUser] = sessions.get(sessionId)
      if(sessionUser.isDefined && sessionUser.get.changeObserver != sender()){
        sessions.put(sessionId, sessionUser.get.copy(changeObserver = sender()))
      }
      sessions.get(sessionId).foreach(_ => world forward msg)
    case msg: BroadcastMessage =>
      sessions.values.foreach(session => session.changeObserver ! msg)
    case _ =>
  }
}
