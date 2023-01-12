package cluster

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

object EchoProtocol {

  def main(args: Array[String]): Unit =
    require(args.length == 2)
    val (port, role) = (args(0), args(1))

    // Override the configuration of the port
    val config =
      ConfigFactory.parseString(
        s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [$role]
      """)
        .withFallback(ConfigFactory.load())

    ActorSystem(EchoNode(), "ClusterSystem", config)
}
