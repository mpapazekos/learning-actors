package protocol_4

import akka.actor.typed.Behavior
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.annotation.tailrec

object StartNode {
  
  // Για τη δημιουργία του είναι απαραίτητο το πλήθος των κόμβων στον δακτύλιο
  def apply(nodesInRing: Int, replyTo: ActorRef[ItaiRodeh.Msg]): Behavior[IRNode.IRMsg] = {

    @tailrec
    def createPath(
                    nodes: Vector[ActorRef[IRNode.IRMsg]],
                    totalNodes: Int,
                    nodesLeft: Int, currNode: ActorRef[IRNode.IRMsg],
                    ctx: ActorContext[IRNode.IRMsg]
                  )
    : Vector[ActorRef[IRNode.IRMsg]] =
      if nodesLeft == totalNodes - 1 then
        nodes
      else
        val nextNode = ctx.spawnAnonymous(IRNode(totalNodes, currNode))
        val nodesUpd = nodes :+ nextNode
        createPath(nodesUpd, totalNodes, nodesLeft + 1, nextNode, ctx)

    // Κατα τη δημιουργία του
    Behaviors.setup { context =>
      // firstNode --> nodesInRing-1 --> nodesInRing-2 --> ... --> nodesInRing --> startNode
      val nodes    = createPath(Vector.empty, nodesInRing, 0, context.self, context)
      val nodesUpd = nodes :+ context.self
      replyTo ! ItaiRodeh.CollectNodes(nodesUpd)
      
      // Η δομή του δακτυλίου ολοκληρώνεται
      // με την επιστροφή ενός κόμβου με γειτονικό κόμβο
      // τον firstNode (startNode --> firstNode)
      IRNode(nodesInRing, nodes.last)
    }
  }
}
