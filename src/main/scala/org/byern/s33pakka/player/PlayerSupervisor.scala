package org.byern.s33pakka.player

import akka.actor.{Actor, ActorRef, Props}
import org.byern.s33pakka.player.Player.{Login, Register}
import org.byern.s33pakka.player.PlayerSupervisor.{AlreadyRegistered, NotExists}

import scala.collection.mutable.Set

object PlayerSupervisor {
  case class AlreadyRegistered(login: String)
  case class NotExists(login: String)

  def props(playerProxy: ActorRef): Props = Props(new PlayerSupervisor(playerProxy))
}

class PlayerSupervisor(val playerProxy: ActorRef) extends Actor {

  private val players: Set[String] = Set[String]()

  override def receive = {
    case msg@Register(login: String, _, _) =>
      players.find(playerLogin => playerLogin == login).fold {
        players += login
        playerProxy forward msg
      } {
        _ => sender() ! AlreadyRegistered(login)
      }
    case msg@Login(login: String, password: String) =>
      players.find(playerLogin => playerLogin == login).fold {
        sender() ! NotExists(login)
      } {
        _ => playerProxy forward Login(login, password)
      }
    case _ =>
      println("Unknown")
  }
}
