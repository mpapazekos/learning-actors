package cluster

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

object RingProtocolCluster {

  def main(args: Array[String]): Unit =
    require(args.length == 2)
    val (port, role) = (args(0), args(1))

    // Override the configuration of the port and role
    val config =
      ConfigFactory.parseString(
        s"""
          akka.remote.artery.canonical.port=$port
          akka.cluster.roles = [$role]
        """)
        .withFallback(ConfigFactory.load())

    val rootBehavior =
      if role == "coord" then
        Coordinator(4)
      else
        RingNode(false, None)

    ActorSystem(rootBehavior, "ClusterSystem", config)
}
