package org.byern.s33pakka.world

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.PersistentActor
import com.fasterxml.jackson.annotation.JsonProperty
import org.byern.s33pakka.core.{Message, Persistable}
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.session.SessionManager.BroadcastMessage
import org.byern.s33pakka.world.World.{ThingAdded, _}

object World {

  trait WorldMsg extends Message

  case class Creature(sign: String, id: String)

  case class Position(x: Int, y: Int)

  case class CreaturePos(creature: Creature, position: Position)

  case class AddThing(creature: Creature, position: Option[Position] = Option.empty) extends WorldMsg
    with ClientMessage

  case class ThingAdded(creaturePos: CreaturePos, msgType: String = "THING_ADDED")
    extends ClientResponse with BroadcastMessage

  case class ThingAddedEvent(creaturePos: CreaturePos) extends Persistable

  case class MoveThing(id: String,
                       @JsonProperty("direction")
                       direction: String) extends WorldMsg with ClientMessage

  case class CantMove(id: String, direction: String)

  case class PositionChanged(id: String, position: Position, msgType: String = "POSITION_CHANGED") extends ClientResponse
     with BroadcastMessage

  case class PositionChangedEvent(id: String, position: Position) extends Persistable

  case class GetState() extends WorldMsg with ClientMessage

  case class State(map: Array[Array[String]], creatures: List[CreaturePos], msgType: String = "STATE") extends ClientResponse

  case class RegisterObserver(actor: ActorRef) extends Persistable

  def props(): Props = Props(new World())
}

class World() extends PersistentActor with ActorLogging {

  private val MAX_WIDTH = 20
  private val MAX_HEIGHT = 20
  var map: Array[Array[String]] = Array.ofDim[String](MAX_WIDTH, MAX_HEIGHT)
  var creatures: scala.collection.mutable.Map[String, CreaturePos] = scala.collection.mutable.Map()
  var changeListener: ActorRef= _

  override def persistenceId = self.path.parent.name + "-" + self.path.name

  override def preStart(): Unit = {
    map = Array(
      Array("X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", "X"),
      Array("X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X", "X")
    )
  }

  override def receiveRecover = {
    case evt: Persistable => updateState(evt)
  }

  override def receiveCommand = {
    case msg @ RegisterObserver(actor: ActorRef) =>
      persist(msg) {
        event => updateState(event)
      }
    case AddThing(creature, position) =>
      creatures.find(_._1 == creature.id).fold {
        val creaturePos = CreaturePos(creature, position.getOrElse(findPosition()))
        persist(ThingAddedEvent(creaturePos)) {
          event => updateState(event)
        }
        val thingAdded = ThingAdded(creaturePos)
        sender() ! thingAdded
        changeListener ! thingAdded
      } {
        _ => log.info("Already exists")
      }
    case MoveThing(id, direction) =>
      creatures.find(_._1 == id).foreach(creature => {
        val oldPosition = creature._2.position
        val newPosition: Position = direction match {
          case "LEFT" => Position(oldPosition.x - 1, oldPosition.y)
          case "RIGHT" => Position(oldPosition.x + 1, oldPosition.y)
          case "UP" => Position(oldPosition.x, oldPosition.y + 1)
          case "DOWN" => Position(oldPosition.x, oldPosition.y - 1)
        }

        if (canMove(newPosition)) {
          persist(PositionChangedEvent(id, newPosition)) {
            event => updateState(event)
          }
          val positionChanged = PositionChanged(id, newPosition)
          sender() ! positionChanged
          changeListener ! positionChanged
        } else {
          sender() ! CantMove(id, direction)
        }
      })
    case GetState() =>
      sender() ! State(map, creatures.values.toList)
    case msg@_ => log.info("unknown message")
  }

  def updateState(obj: Persistable) = {
    obj match {
      case event@ThingAddedEvent(_) =>
        creatures.put(event.creaturePos.creature.id, event.creaturePos)
      case event@PositionChangedEvent(_, _) =>
        creatures.get(event.id).foreach(creature =>
          creatures.put(event.id, creature.copy(position = event.position))
        )
      case RegisterObserver(actor) =>
        changeListener = actor
    }
  }

  def findPosition(): Position = {
    val random = scala.util.Random
    val newPos = Position(random.nextInt(MAX_WIDTH), random.nextInt(MAX_HEIGHT))
    if (canMove(newPos)) {
      newPos
    } else {
      findPosition()
    }
  }

  def canMove(pos: Position): Boolean = {
    pos.x >= 0 && pos.y >= 0 && pos.x < MAX_WIDTH && pos.y < MAX_HEIGHT &&
      map(pos.x)(pos.y) == " " &&
      !creatures.values.exists(creaturePos => creaturePos.position == pos)
  }
}
