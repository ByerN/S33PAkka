package org.byern.s33pakka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.testkit.{ImplicitSender, TestKit}
import org.byern.s33pakka.world.GameLevel
import org.byern.s33pakka.world.GameLevel.GetState
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class GameLevelTest extends TestKit(ActorSystem("system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  var playerProxy: ActorRef = _

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll {
    val store = system.actorOf(Props[SharedLeveldbStore], "store")
  }

  "GameLevel actor" must {
    "be properly initialized" in {
      val gameLevel = system.actorOf(GameLevel.props())
      gameLevel ! GetState()
      val state: GameLevel.State = expectMsgClass(classOf[GameLevel.State])
      assert(state.creatures.isEmpty)
      assert(state.map.length == 20)
    }
  }

  "GameLevel actor" must {
    "create new thing when needed" in {
      val gameLevel = system.actorOf(GameLevel.props())
      gameLevel ! GameLevel.AddThing(GameLevel.Creature("a", "id1"))
      val thing: GameLevel.ThingAdded = expectMsgClass(classOf[GameLevel.ThingAdded])
      assert(thing.creaturePos.creature.id == "id1")
      assert(thing.creaturePos.creature.sign == "a")
    }
  }

  "GameLevel actor" must {
    "properly move thing" in {
      val gameLevel = system.actorOf(GameLevel.props())
      gameLevel ! GameLevel.AddThing(GameLevel.Creature("a", "id1"), Option(GameLevel.Position(10, 10)))
      val thing: GameLevel.ThingAdded = expectMsgClass(classOf[GameLevel.ThingAdded])
      assert(thing.creaturePos.creature.id == "id1")
      assert(thing.creaturePos.creature.sign == "a")
      assert(thing.creaturePos.position.x == 10)
      assert(thing.creaturePos.position.y == 10)
      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Left())
      expectMsg(GameLevel.PositionChanged("id1", GameLevel.Position(9, 10)))

      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Right())
      expectMsg(GameLevel.PositionChanged("id1", GameLevel.Position(10, 10)))

      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Up())
      expectMsg(GameLevel.PositionChanged("id1", GameLevel.Position(10, 11)))

      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Down())
      expectMsg(GameLevel.PositionChanged("id1", GameLevel.Position(10, 10)))

    }
  }

  "GameLevel actor" must {
    "properly detect collision with walls" in {
      val gameLevel = system.actorOf(GameLevel.props())
      gameLevel ! GameLevel.AddThing(GameLevel.Creature("a", "id1"), Option(GameLevel.Position(1, 1)))
      expectMsgClass(classOf[GameLevel.ThingAdded])
      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Down())
      expectMsg(GameLevel.CantMove("id1", GameLevel.Down()))
      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Left())
      expectMsg(GameLevel.CantMove("id1", GameLevel.Left()))

      gameLevel ! GameLevel.AddThing(GameLevel.Creature("a", "id2"), Option(GameLevel.Position(18, 18)))
      expectMsgClass(classOf[GameLevel.ThingAdded])
      gameLevel ! GameLevel.MoveThing("id2", GameLevel.Up())
      expectMsg(GameLevel.CantMove("id2", GameLevel.Up()))
      gameLevel ! GameLevel.MoveThing("id2", GameLevel.Right())
      expectMsg(GameLevel.CantMove("id2", GameLevel.Right()))
    }
  }

  "GameLevel actor" must {
    "properly detect collision with other things" in {
      val gameLevel = system.actorOf(GameLevel.props())
      gameLevel ! GameLevel.AddThing(GameLevel.Creature("a", "id1"), Option(GameLevel.Position(5, 6)))
      expectMsgClass(classOf[GameLevel.ThingAdded])
      gameLevel ! GameLevel.AddThing(GameLevel.Creature("a", "id2"), Option(GameLevel.Position(5, 5)))
      expectMsgClass(classOf[GameLevel.ThingAdded])

      gameLevel ! GameLevel.MoveThing("id1", GameLevel.Down())
      expectMsg(GameLevel.CantMove("id1", GameLevel.Down()))
      gameLevel ! GameLevel.MoveThing("id2", GameLevel.Up())
      expectMsg(GameLevel.CantMove("id2", GameLevel.Up()))
    }
  }
}