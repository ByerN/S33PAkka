package org.byern.s33pakka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.testkit.{ImplicitSender, TestKit}
import org.byern.s33pakka.config.{ShardMessageConfiguration, SharedStoreUsage}
import org.byern.s33pakka.player.Player
import org.byern.s33pakka.player.Player._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PlayerTest() extends TestKit(ActorSystem("system")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  var playerProxy: ActorRef = _

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  override def beforeAll {
    system.actorOf(Props[SharedLeveldbStore], "store")
    system.actorOf(Props[SharedStoreUsage])
    ClusterSharding(system).start(
      typeName = "player",
      entityProps = Player.props(),
      settings = ClusterShardingSettings(system),
      extractEntityId = ShardMessageConfiguration.extractEntityId,
      extractShardId = ShardMessageConfiguration.extractShardId
    )
    playerProxy = ClusterSharding(system).shardRegion("player")
  }

  "Player actor" must {
    "send back Initialized if it's initialized" in {
      val player = system.actorOf(Player.props())
      player ! Player.Register("a3", "b", "c", "a3")
      expectMsg(Player.Registered("a3", "c"))
    }

  }

  "Player actor" must {
    "send back Error and stash messages if before initialized" in {
      val player = system.actorOf(Player.props())
      player ! Login("a4", "b", "a4")
      expectMsg(NotInitialized())
      player ! Player.Register("a4", "b", "c", "a3")
      expectMsg(Player.Registered("a4", "c"))
      player ! Login("a4", "b", "a4")
      expectMsg(CorrectPassword("a4", "c"))
    }
  }

  "Player actor" must {
    "not pass if password is incorrect" in {
      val player = system.actorOf(Player.props())
      player ! Player.Register("a5", "b", "c", "a3")
      expectMsg(Player.Registered("a5", "c"))
      player ! Player.Login("a5", "incorrect password", "a5")
      expectMsg(IncorrectPassword("a5"))
    }
  }
}