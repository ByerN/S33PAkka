package org.byern.s33pakka

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.server.Directives
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.{ConfigFactory, ConfigValue}
import org.byern.s33pakka.MainApp.config
import org.byern.s33pakka.config.{ShardMessageConfiguration, SharedStoreUsage}
import org.byern.s33pakka.controller.WebsocketServer
import org.byern.s33pakka.player.Player
import org.byern.s33pakka.session.SessionManager
import org.byern.s33pakka.world.World

import scala.io.StdIn

object MainApp extends App with Directives {

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)
  var config = ConfigFactory.load()

  if (args.length > 0) {
    val port = args(0).toInt
    config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load)
  }

  implicit val system = ActorSystem.create("system", config)
  ClusterSharding(system).start(
    typeName = "player",
    entityProps = Player.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = ShardMessageConfiguration.extractEntityId,
    extractShardId = ShardMessageConfiguration.extractShardId
  )
  val playerProxy = ClusterSharding(system).shardRegion("player")

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = World.props(),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)),
    name = "world")
  val world = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/world",
      settings = ClusterSingletonProxySettings(system)),
    name = "worldProxy")

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = SessionManager.props(ClusterSharding(system).shardRegion("player"),
        world),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)),
    name = "sessionManager")
  val sessionManager = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/sessionManager",
      settings = ClusterSingletonProxySettings(system)),
    name = "sessionManagerProxy")

  world ! World.RegisterObserver(sessionManager)

  system.actorOf(
    ClusterSingletonManager.props(
      singletonProps = WebsocketServer.props(sessionManager),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)),
    name = "websocketSever")
  val websocketSever = system.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/websocketSever",
      settings = ClusterSingletonProxySettings(system)),
    name = "websocketSeverProxy")

  try StdIn.readLine()
  finally system.terminate()

}