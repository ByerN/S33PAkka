package org.byern.s33pakka.config

import akka.actor.{Actor, ActorIdentity, Identify}
import akka.persistence.journal.leveldb.SharedLeveldbJournal

class SharedStoreUsage extends Actor {
  override def preStart(): Unit = {
    context.actorSelection("akka.tcp://system@127.0.0.1:2222/user/store") ! Identify(1)
  }

  def receive = {
    case ActorIdentity(1, Some(store)) =>
      SharedLeveldbJournal.setStore(store, context.system)
  }
}
