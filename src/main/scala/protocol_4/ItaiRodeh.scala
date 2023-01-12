package protocol_4

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}


object ItaiRodeh {
  
  //Fokkink, Wan & Pang, Jun. (2004). Simplifying Itai-Rodeh Leader Election for Anonymous Rings.
  // Electronic Notes in Theoretical Computer Science. 128. 53-68. 10.1016/j.entcs.2005.04.004.

  //Itai and Rodeh studied how to break the symmetry in anonymous net-
  //works using probabilistic algorithms. They presented a probabilistic algorithm to
  //elect a leader in the above network model, under the assumption that processes
  //know that the size of the ring is n. It is a Las Vegas algorithm that terminates
  //with probability one. The Itai-Rodeh algorithm is based on the Chang-Roberts
  //algorithm, where processes are assumed have unique identities, and each
  //process sends out a message carrying its identity. Only the message with the
  //largest identity completes the round trip and returns to its originator, which
  //becomes the leader.

  //In the Itai-Rodeh algorithm, each process selects a random identity from a
  //ﬁnite set. So different processes may carry the same identity. Again each process
  //sends out a message carrying its identity. Messages are supplied with a hop
  //counter, so that a process can recognize its own message (by checking whether
  //the hop counter equals the ring size n). Moreover, a process with the largest
  //identity present in the ring must be able to detect whether there are other
  //processes in the ring with the same identity. Therefore each message is supplied
  //with a bit, which is dirtied when it passes a process that is not its originator
  //but shares the same identity. When a process receives its own message, either it
  //becomes the leader (if the bit is clean), or it selects a new identity and starts the
  //next election round (if the bit is dirty). In this next election round, only processes
  //that shared the largest identity in the ring are active. All other processes have
  //been made passive by the receipt of a message with an identity larger than their
  //own. The active processes maintain a round number, which initially starts at
  //zero and is augmented at each new election round. Thus messages from earlier
  //election rounds can be recognized and ignored.

  def main(args: Array[String]): Unit =
    ActorSystem(ItaiRodeh(3), "ItaiRodeh")
  
  //===============================================================================
  
  sealed trait Msg
  final case class CollectNodes(nodes: Vector[ActorRef[IRNode.IRMsg]]) extends Msg

  def apply(totalNodes: Int): Behavior[Msg] =
    Behaviors.setup { ctx =>
      ctx.spawnAnonymous(StartNode(totalNodes, ctx.self))
      Behaviors.receiveMessage {
        case CollectNodes(nodes) =>
          nodes.foreach(node => node ! IRNode.StartNewElection )
          Behaviors.empty
      }
    }
}
