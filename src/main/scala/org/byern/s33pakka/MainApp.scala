package org.byern.s33pakka

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.journal.leveldb.SharedLeveldbStore
import org.byern.s33pakka.config.ShardMessageConfiguration
import org.byern.s33pakka.player.{Player, PlayerSupervisor}

import scala.io.StdIn

object MainApp extends App {
  val system = ActorSystem("system")
  val store = system.actorOf(Props[SharedLeveldbStore], "store")
  val playerProxy: ActorRef = ClusterSharding(system).start(
    typeName = "player",
    entityProps = Player.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = ShardMessageConfiguration.extractEntityId,
    extractShardId = ShardMessageConfiguration.extractShardId
  )

  val playerSupervisor = system.actorOf(PlayerSupervisor.props(ClusterSharding(system).shardRegion("player")), "playerSupervisor")

  println(">>> Press ENTER to exit <<<")

  try StdIn.readLine()
  finally system.terminate()

}