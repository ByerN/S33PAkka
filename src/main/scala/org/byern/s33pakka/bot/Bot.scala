package org.byern.s33pakka.bot

import akka.actor.ActorRef
import akka.persistence.PersistentActor
import org.byern.s33pakka.bot.Bot.Init
import org.byern.s33pakka.core.Persistable

import scala.util.Random

object Bot {

  case class Init(login: String = Random.nextString(5),
                  password: String = Random.nextString(10),
                  sign: String = Random.nextString(1)
                 ) extends Persistable

}

class Bot extends PersistentActor {

  var login: String = _
  var password: String = _
  var sign: String = _

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  override def receiveRecover = {
    case msg: Persistable =>
      updateState(msg)
  }

  override def receiveCommand = {
    case msg: Init =>
      persist(msg)(event => updateState(event))
  }

  def updateState(event: Persistable): Unit = {
    event match {
      case Init(login: String, password: String, sign: String) =>
        this.login = login
        this.password = password
        this.sign = sign
    }
  }
}
