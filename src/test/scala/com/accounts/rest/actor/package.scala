package com.accounts.rest

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

package object actor {
  val config = ConfigFactory.parseString(
    """
      akka.loggers = ["akka.testkit.TestEventListener"]
      akka.persistence.journal.plugin = "inmemory-journal"
    """)
}
