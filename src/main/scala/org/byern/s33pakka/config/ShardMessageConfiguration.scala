package org.byern.s33pakka.config

import akka.cluster.sharding.ShardRegion
import org.byern.s33pakka.core.ShardMessage

object ShardMessageConfiguration {

  var shardNumber: Int = 10

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case msg:ShardMessage => {
      (msg.entityId, msg)
    }
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case msg:ShardMessage => (msg.entityId.hashCode % shardNumber).toString
    case ShardRegion.StartEntity(id) ⇒ {
      (id.toLong % shardNumber).toString
    }
  }
}
