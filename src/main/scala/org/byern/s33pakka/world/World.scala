package org.byern.s33pakka.world

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor
import org.byern.s33pakka.core.{Message, Persistable}
import org.byern.s33pakka.world.World._

object World {

  trait WorldMsg extends Message

  case class Creature(sign: String, id: String)

  case class Position(x: Int, y: Int)

  case class CreaturePos(creature: Creature, position: Position)

  case class AddThing(creature: Creature, position: Option[Position] = Option.empty) extends WorldMsg

  case class ThingAdded(creaturePos: CreaturePos) extends Persistable

  case class MoveThing(id: String, direction: Direction) extends WorldMsg

  case class CantMove(id: String, direction: Direction)

  case class PositionChanged(id: String, position: Position) extends Persistable

  case class GetState() extends WorldMsg

  case class State(map: Array[Array[String]], creatures: List[CreaturePos])

  trait Direction {
    def move(position: Position): Position
  }

  case class Left() extends Direction {
    override def move(position: Position): Position = Position(position.x - 1, position.y)
  }

  case class Right() extends Direction {
    override def move(position: Position): Position = Position(position.x + 1, position.y)
  }

  case class Up() extends Direction {
    override def move(position: Position): Position = Position(position.x, position.y + 1)
  }

  case class Down() extends Direction {
    override def move(position: Position): Position = Position(position.x, position.y - 1)
  }

  def props(): Props = Props(new World())
}

class World extends PersistentActor with ActorLogging {

  private val MAX_WIDTH = 20
  private val MAX_HEIGHT = 20
  var map: Array[Array[String]] = Array.ofDim[String](MAX_WIDTH, MAX_HEIGHT)
  var creatures: scala.collection.mutable.Map[String, CreaturePos] = scala.collection.mutable.Map()

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
    case AddThing(creature, position) =>
      creatures.find(_._1 == creature.id).fold {
        val creaturePos = CreaturePos(creature, position.getOrElse(findPosition()))
        val thingAddedEvent = ThingAdded(creaturePos)
        persist(thingAddedEvent) {
          event => updateState(event)
        }
        sender() ! thingAddedEvent
      } {
        _ => log.info("Already exists")
      }
    case MoveThing(id, direction) =>
      creatures.find(_._1 == id).foreach(creature => {
        val newPosition: Position = direction.move(creature._2.position)
        if (canMove(newPosition)) {
          val positionChangedEvent = PositionChanged(id, newPosition)
          persist(positionChangedEvent) {
            event => updateState(event)
          }
          sender() ! positionChangedEvent
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
      case event@ThingAdded(_) =>
        creatures.put(event.creaturePos.creature.id, event.creaturePos)
      case event@PositionChanged(_, _) =>
        creatures.get(event.id).foreach(creature =>
          creatures.put(event.id, creature.copy(position = event.position))
        )
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
