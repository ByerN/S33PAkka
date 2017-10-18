package org.byern.s33pakka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.testkit.{ImplicitSender, TestKit}
import org.byern.s33pakka.world.World
import org.byern.s33pakka.world.World.GetState
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class GameLevelTest extends TestKit(ActorSystem("system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll {
    val store = system.actorOf(Props[SharedLeveldbStore], "store")
  }

  "GameLevel actor" must {
    "be properly initialized" in {
      val gameLevel = system.actorOf(World.props())
      gameLevel ! GetState()
      val state: World.State = expectMsgClass(classOf[World.State])
      assert(state.creatures.isEmpty)
      assert(state.map.length == 20)
    }
  }

  "GameLevel actor" must {
    "create new thing when needed" in {
      val gameLevel = system.actorOf(World.props())
      gameLevel ! World.AddThing(World.Creature("a", "id1"))
      val thing: World.ThingAdded = expectMsgClass(classOf[World.ThingAdded])
      assert(thing.creaturePos.creature.id == "id1")
      assert(thing.creaturePos.creature.sign == "a")
    }
  }

  "GameLevel actor" must {
    "properly move thing" in {
      val gameLevel = system.actorOf(World.props())
      gameLevel ! World.AddThing(World.Creature("a", "id1"), Option(World.Position(10, 10)))
      val thing: World.ThingAdded = expectMsgClass(classOf[World.ThingAdded])
      assert(thing.creaturePos.creature.id == "id1")
      assert(thing.creaturePos.creature.sign == "a")
      assert(thing.creaturePos.position.x == 10)
      assert(thing.creaturePos.position.y == 10)
      gameLevel ! World.MoveThing("id1", "LEFT")
      expectMsg(World.PositionChanged("id1", World.Position(9, 10)))

      gameLevel ! World.MoveThing("id1", "RIGHT")
      expectMsg(World.PositionChanged("id1", World.Position(10, 10)))

      gameLevel ! World.MoveThing("id1", "UP")
      expectMsg(World.PositionChanged("id1", World.Position(10, 11)))

      gameLevel ! World.MoveThing("id1", "DOWN")
      expectMsg(World.PositionChanged("id1", World.Position(10, 10)))

    }
  }

  "GameLevel actor" must {
    "properly detect collision with walls" in {
      val gameLevel = system.actorOf(World.props())
      gameLevel ! World.AddThing(World.Creature("a", "id1"), Option(World.Position(1, 1)))
      expectMsgClass(classOf[World.ThingAdded])
      gameLevel ! World.MoveThing("id1", "DOWN")
      expectMsg(World.CantMove("id1", "DOWN"))
      gameLevel ! World.MoveThing("id1", "LEFT")
      expectMsg(World.CantMove("id1", "LEFT"))

      gameLevel ! World.AddThing(World.Creature("a", "id2"), Option(World.Position(18, 18)))
      expectMsgClass(classOf[World.ThingAdded])
      gameLevel ! World.MoveThing("id2", "UP")
      expectMsg(World.CantMove("id2", "UP"))
      gameLevel ! World.MoveThing("id2", "RIGHT")
      expectMsg(World.CantMove("id2", "RIGHT"))
    }
  }

  "GameLevel actor" must {
    "properly detect collision with other things" in {
      val gameLevel = system.actorOf(World.props())
      gameLevel ! World.AddThing(World.Creature("a", "id1"), Option(World.Position(5, 6)))
      expectMsgClass(classOf[World.ThingAdded])
      gameLevel ! World.AddThing(World.Creature("a", "id2"), Option(World.Position(5, 5)))
      expectMsgClass(classOf[World.ThingAdded])

      gameLevel ! World.MoveThing("id1", "DOWN")
      expectMsg(World.CantMove("id1", "DOWN"))
      gameLevel ! World.MoveThing("id2", "UP")
      expectMsg(World.CantMove("id2", "UP"))
    }
  }
}