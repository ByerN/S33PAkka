package org.byern.s33pakka.config

import akka.cluster.sharding.ShardRegion
import org.byern.s33pakka.core.BaseShardMessage

object ShardMessageConfiguration {

  var shardNumber: Int = 10

  def extractEntityId: ShardRegion.ExtractEntityId = {
    case msg:BaseShardMessage => {
      (msg.entityId, msg)
    }
  }

  def extractShardId: ShardRegion.ExtractShardId = {
    case msg:BaseShardMessage => (msg.entityId.hashCode % shardNumber).toString
    case ShardRegion.StartEntity(id) ⇒ {
      (id.toLong % shardNumber).toString
    }
  }
}
