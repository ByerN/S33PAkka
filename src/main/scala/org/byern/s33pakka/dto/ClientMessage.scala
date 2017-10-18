package org.byern.s33pakka.dto

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import org.byern.s33pakka.core.Message
import org.byern.s33pakka.player.Player
import org.byern.s33pakka.session.SessionManager.SessionMessage
import org.byern.s33pakka.world.World

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new Type(value = classOf[Player.Register], name = "REGISTER"),
  new Type(value = classOf[Player.Login], name = "LOGIN"),
  new Type(value = classOf[World.MoveThing], name = "MOVE"),
  new Type(value = classOf[World.GetState], name = "STATE"),
  new Type(value = classOf[SessionMessage], name = "SESSION_MESSAGE")
))
trait ClientMessage extends Message