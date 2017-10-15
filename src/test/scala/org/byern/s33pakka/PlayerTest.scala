package org.byern.s33pakka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.ddata.Replicator.NotFound
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.testkit.{ImplicitSender, TestKit}
import org.byern.s33pakka.config.{ShardMessageConfiguration, SharedStoreUsage}
import org.byern.s33pakka.player.Player._
import org.byern.s33pakka.player.PlayerSupervisor.{AlreadyRegistered, Login, NotExists, Register}
import org.byern.s33pakka.player.{Player, PlayerSupervisor}
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

  "PlayerSupervisor actor" must {
    "send back not found if player is not registered" in {
      val playerSupervisor = system.actorOf(PlayerSupervisor.props(playerProxy))
      playerSupervisor ! Register("a1", "b", "c")
      expectMsg(Initialized("a1", "c"))
      playerSupervisor ! Register("a1", "b", "c")
      expectMsg(AlreadyRegistered("a1"))
    }
  }

  "PlayerSupervisor actor" must {
    "not login at not registered account" in {
      val playerSupervisor = system.actorOf(PlayerSupervisor.props(playerProxy))
      playerSupervisor ! Login("a2", "b")
      expectMsg(NotExists("a2"))
    }
  }

  "PlayerSupervisor actor" must {
    "login at registered account" in {
      val playerSupervisor = system.actorOf(PlayerSupervisor.props(playerProxy))
      playerSupervisor ! Register("a2", "b", "c")
      expectMsg(Initialized("a2", "c"))
      playerSupervisor ! Login("a2", "b")
      expectMsg(CorrectPassword("c"))
    }
  }

  "Player actor" must {
    "send back Initialized if it's initialized" in {
      val player = system.actorOf(Player.props())
      player ! Init("a3", "b", "c")
      expectMsg(Initialized("a3", "c"))
    }

  }

  "Player actor" must {
    "send back Error and stash messages if before initialized" in {
      val player = system.actorOf(Player.props())
      player ! Login("a4", "b")
      expectMsg(NotInitialized())
      player ! Init("a4", "b", "c")
      expectMsg(Initialized("a4", "c"))
      expectMsg(CorrectPassword("c"))
    }
  }

  "Player actor" must {
    "not pass if password is incorrect" in {
      val player = system.actorOf(Player.props())
      player ! Init("a5", "b", "c")
      expectMsg(Initialized("a5", "c"))
      player ! Login("a5", "incorrect password")
      expectMsg(IncorrectPassword())
    }
  }
}