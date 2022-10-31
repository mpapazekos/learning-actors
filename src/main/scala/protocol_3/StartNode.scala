package protocol_3

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import protocol_3.ChangAndRoberts.RingMsg

import scala.annotation.tailrec
import scala.util.Random

object StartNode:

  // Για τη δημιουργία του είναι απαραίτητο
  // το πλήθος των κόμβων στον δακτύλιο
  def apply(nodesInRing: Int): Behavior[RingMsg] = {

    // Ίδια μέθοδος που χρησιμοποιείται και στο πρωτόκολλο_2
    @tailrec
    def createPath(currNode: ActorRef[RingMsg], nodesLeft: Int, ctx: ActorContext[RingMsg]): ActorRef[RingMsg] =
      if nodesLeft == 0 then
        currNode
      else
        val randId = Random.between(0L, 10000L)
        val nextNode = ctx.spawn(SimpleNode(randId, currNode, None), s"SimpleNode:$randId")
        createPath(nextNode, nodesLeft - 1, ctx)

    // Κατα τη δημιουργία του
    Behaviors.setup { context =>
      // firstNode --> nodesInRing-1 --> nodesInRing-2 --> ... --> nodesInRing --> startNode
      val firstNode = createPath(context.self, nodesInRing, context)
      val randId = Random.between(0L, 1000L)

      // Η δομή του δακτυλίου ολοκληρώνεται
      // με την επιστροφή ενός κόμβου με γειτονικό κόμβο
      // τον firstNode (startNode --> firstNode)
      SimpleNode(randId, firstNode, None)
    }
  }
