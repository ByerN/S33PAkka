package org.byern.s33pakka

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.config.{Config, ConfigFactory}
import org.byern.s33pakka.config.{ShardMessageConfiguration, SharedStoreUsage}
import org.byern.s33pakka.dto.{ClientMessage, ClientResponse}
import org.byern.s33pakka.player.Player
import org.byern.s33pakka.world.World

import scala.io.StdIn

object MainApp extends App with Directives {

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  var port = 2222
  if (args.length > 0) port = args(0).toInt
  val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load)

  implicit val system = ActorSystem.create("system", config)


  implicit val materializer = ActorMaterializer()

  system.actorOf(Props[SharedLeveldbStore], "store")
  system.actorOf(Props[SharedStoreUsage])

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

  def newUser(): Flow[Message, Message, NotUsed] = {
    val connectedWsActor = system.actorOf(ConnectedUser.props(sessionManager))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) =>
          println(text)
          try {
            mapper.readValue(text, classOf[ClientMessage]) match {
              case message: core.Message =>
                println(message)
                message
            }
          }
          catch {
            case e: Exception =>
              println("Bad message!")
              println(e)
              core.Message.Null()
          }
      }.to(Sink.actorRef[core.Message](connectedWsActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[ClientResponse](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          // give the user actor a way to send messages out
          connectedWsActor ! ConnectedUser.Connected(outActor)
          NotUsed
        }.map(
        // transform domain message to web socket message

        (outMsg: ClientResponse) => {
          println("outL: " + mapper.writeValueAsString(outMsg))
          TextMessage(mapper.writeValueAsString(outMsg))
        }
      )

    // then combine both to a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  val route =
    path("example") {
      get {
        handleWebSocketMessages(newUser())
      }
    }

  Http().bindAndHandle(route, "127.0.0.1", 8080)

  try StdIn.readLine()
  finally system.terminate()

}