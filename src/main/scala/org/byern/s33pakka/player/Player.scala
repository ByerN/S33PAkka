package org.byern.s33pakka.player

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import org.byern.s33pakka.core.{BaseShardMessage, Persistable}
import org.byern.s33pakka.player.Player._
import org.byern.s33pakka.player.PlayerSupervisor.Login

object Player {

  val entityIdPrefix = "player"

  case class Init(login: String, password: String, sign: String) extends BaseShardMessage(entityIdPrefix + "_" + login)

  case class Initialized(login: String, sign: String)

  case class NotInitialized()

  case class IncorrectPassword()

  case class CorrectPassword(sign: String)

  case class InitializedEvent(login: String, password: String, sign: String) extends Persistable

  def props(): Props = Props(new Player())
}

class Player extends PersistentActor with ActorLogging {

  var login: String = ""
  var password: String = ""
  var sign: String = ""

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  override def receiveRecover = {
    case evt: Persistable =>
      updateState(evt)
  }

  def notInitializedReceive: Receive = {
    case Init(login: String, password: String, sign: String) =>
      persist(InitializedEvent(login, password, sign)) { event =>
        updateState(event)
      }
      sender() ! Initialized(login, sign)
      context.become(initializedReceive)
      unstashAll()
    case _ =>
      sender() ! NotInitialized()
      stash()
  }

  def initializedReceive: Receive = {
    case Login(_, password: String) =>
      if (this.password == password)
        sender() ! CorrectPassword(sign)
      else
        sender() ! IncorrectPassword()
    case msg@_ =>
      log.info("Unknown message " + msg)
  }

  override def receiveCommand = notInitializedReceive

  def updateState(event: Persistable): Unit = event match {
    case InitializedEvent(login: String, password: String, sign: String) =>
      this.login = login
      this.password = password
      this.sign = sign
      context.become(initializedReceive)
  }
}
