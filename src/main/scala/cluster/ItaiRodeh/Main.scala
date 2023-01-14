package cluster.ItaiRodeh

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

object Main {

  def main(args: Array[String]): Unit =
    require(args.length == 3)

    val (port, role, id) = (args(0), args(1), args(2))
  
    val totalNodes = Integer.parseInt(id)
    
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
        Coordinator(totalNodes)
      else
        IRNode(totalNodes)

    ActorSystem(rootBehavior, "ClusterSystem", config)
}