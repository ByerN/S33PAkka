package org.byern.s33pakka.player

import akka.actor.{Actor, ActorRef, Props}
import org.byern.s33pakka.core.BaseShardMessage
import org.byern.s33pakka.player.Player.{Init, entityIdPrefix}
import org.byern.s33pakka.player.PlayerSupervisor.{AlreadyRegistered, Login, NotExists, Register}

import scala.collection.mutable.Set

object PlayerSupervisor {
  case class Register(login: String, password: String, sign: String)
  case class AlreadyRegistered(login: String)

  case class Login(login: String, password: String)extends BaseShardMessage(entityIdPrefix + "_" + login)
  case class NotExists(login: String)

  def props(playerProxy: ActorRef): Props = Props(new PlayerSupervisor(playerProxy))
}

class PlayerSupervisor(val playerProxy: ActorRef) extends Actor {

  private val players: Set[String] = Set[String]()

  override def receive = {
    case Register(login: String, password: String, sign: String) =>
      players.find(playerLogin => playerLogin == login).fold {
        players += login
        playerProxy forward Init(login, password, sign)
      } {
        _ => sender() ! AlreadyRegistered(login)
      }
    case Login(login: String, password: String) =>
      players.find(playerLogin => playerLogin == login).fold {
        sender() ! NotExists(login)
      } {
        _ => playerProxy forward Login(login, password)
      }
    case _ =>
      println("Unknown")
  }
}
