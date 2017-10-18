package org.byern.s33pakka.player

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import com.fasterxml.jackson.annotation.JsonProperty
import org.byern.s33pakka.core.{Message, Persistable, ShardMessage}
import org.byern.s33pakka.dto.ClientMessage
import org.byern.s33pakka.player.Player._

object Player {

  val PREFIX = "player"

  trait PlayerMsg extends Message

  case class Register(
                       @JsonProperty("login")
                       login: String,
                       @JsonProperty("password")
                       password: String,
                       @JsonProperty("sign")
                       sign: String) extends ShardMessage(PREFIX + "_" + login)
    with PlayerMsg with ClientMessage

  case class Registered(login: String, sign: String)

  case class AlreadyRegistered(login: String)

  case class NotExists(login: String)

  case class NotInitialized()

  case class Login(
                    @JsonProperty("login")
                    login: String,
                    @JsonProperty("password")
                    password: String) extends ShardMessage(PREFIX + "_" + login)
    with PlayerMsg with ClientMessage

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
    case msg@Login =>
      sender() ! NotExists(login)
    case _ =>
      sender() ! NotInitialized()
  }

  def initialized: Receive = {
    case Login(_, password: String) =>
      if (this.password == password)
        sender() ! CorrectPassword(login, sign)
      else
        sender() ! IncorrectPassword(login)
    case msg@Register =>
      sender() ! AlreadyRegistered(login)
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
