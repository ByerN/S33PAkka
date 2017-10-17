package org.byern.s33pakka.player

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import org.byern.s33pakka.core.{Message, Persistable, ShardMessage}
import org.byern.s33pakka.player.Player._

object Player {

  val PREFIX = "player"

  trait PlayerMsg extends Message

  case class Register(login: String, password: String, sign: String) extends ShardMessage(PREFIX + "_" + login) with PlayerMsg

  case class Registered(login: String, sign: String)

  case class NotInitialized()

  case class Login(login: String, password: String) extends ShardMessage(PREFIX + "_" + login) with PlayerMsg

  case class IncorrectPassword(login: String)

  case class CorrectPassword(login: String, sign: String)

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

  def notInitialized: Receive = {
    case Register(login: String, password: String, sign: String) =>
      persist(InitializedEvent(login, password, sign)) { event =>
        updateState(event)
      }
      sender() ! Registered(login, sign)
      unstashAll()
    case _ =>
      sender() ! NotInitialized()
      stash()
  }

  def initialized: Receive = {
    case Login(_, password: String) =>
      if (this.password == password)
        sender() ! CorrectPassword(login, sign)
      else
        sender() ! IncorrectPassword(login)
    case msg@_ =>
      log.info("Unknown message " + msg)
  }

  override def receiveCommand = notInitialized

  def updateState(event: Persistable): Unit = event match {
    case InitializedEvent(login: String, password: String, sign: String) =>
      this.login = login
      this.password = password
      this.sign = sign
      context become initialized
  }
}
