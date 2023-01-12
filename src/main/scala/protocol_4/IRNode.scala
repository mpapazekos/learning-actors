package protocol_4

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.util.Random
import akka.actor.typed.Behavior

object IRNode {

  //Each process pi maintains three parameters:
  //- id i ∈ {1, . . . , k}, for some k ≥ 2, is its identity;
  //- state i ranges over {active, passive, leader };
  //- round i ∈ N represents the number of the current election round.
  //Only active processes may become the leader; passive processes simply pass on messages.
  private case class NodeStats(
                                id: Int, 
                                round: Int, 
                                state: IRNState, 
                                totalNodes: Int,
                                neighbor: ActorRef[IRMsg]
                              )

  enum IRNState:
    case Passive, Active, Leader

  //At the start of a new election round, each active process sends a
  //message of the form (id , round, hop, bit), where:
  // - the values of id and round are taken from the process that sends the message;
  // - hop is a counter that initially has the value one, and which is increased by
  //   one every time it is passed on by a process;
  // - bit is a bit that initially is true, and which is set to false when it visits a
  //   process that has the same identity but that is not its originator.
  sealed trait IRMsg
  case object StartNewElection extends IRMsg
  private final case class RoundMsg(id: Int, round: Int, hop: Int, sameIdFound: Boolean) extends IRMsg

  def apply(totalNodes: Int, neighbor: ActorRef[IRMsg]): Behavior[IRMsg] =
    Behaviors.setup { ctx =>
      val randId = Random.between(0, totalNodes) + 1
      val node = NodeStats(randId, 0, IRNState.Active, totalNodes, neighbor)

      ctx.log.info(s"Anon[id = $randId]")
      nodeRep(node)
    }

  private def nodeRep(node: NodeStats): Behavior[IRMsg] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case StartNewElection =>
          node.neighbor ! RoundMsg(node.id, node.round, 1, false)
          Behaviors.same
        case rMsg @ RoundMsg(_, _, hop, _) =>
          node.state match
            case IRNState.Passive =>
              //passes on the message, increasing the counter hop by one
              node.neighbor ! rMsg.copy(hop = hop + 1)
              Behaviors.same
            case IRNState.Active =>
              // Καλεί την αντίστοιχη μέθοδο για διαχείριση της περίπτωσης
              onActive(rMsg, node, ctx)
            case IRNState.Leader =>
              Behaviors.same
    }

  //Κάθε ενεργός κόμβος με τη λήψη του μηνύματος  (id, round, hop, sameIdFound) κάνει τα παρακάτω:
  private def onActive(msg: RoundMsg, node: NodeStats, ctx: ActorContext[IRMsg]): Behavior[IRMsg] =

    // To μήνυμα αφορά νέο γύρο εκλογών οπότε δεν ενδιαφέρει τον τρέχοντα κόμβο.
    // Το μήνυμα προωθείται
    if msg.round > node.round then
      val newMsg = msg.copy(hop = msg.hop + 1)
      node.neighbor ! newMsg
      Behaviors.same
    else
      // Το μήνυμα έχει κάνει hops ίδιο με το totalNodes που γνωρίζει ο κόμβος
      // αρα πρόκειται για το αρχικό μήνυμα που εστειλε και περασε απο όλους τους κόμβους
      if msg.hop == node.totalNodes then
        if msg.sameIdFound then
          // έχει βρεθεί ίδιο id οπότε ξεκινάει νέο γύρο εκλογών
          ctx.self ! StartNewElection

          val nextId = Random.between(0, node.totalNodes) + 1
          val nextRound = node.round + 1
          val newState = node.copy(id = nextId, round = nextRound)
          ctx.log.info(s"Node with id ${node.id} starting new election(newId: $nextId, round: $nextRound)")
          nodeRep(newState)
        else
          // δεν έχει βρεθεί ίδιο id οπότε γίνεται αρχηγός
          val newState = node.copy(state = IRNState.Leader)
          ctx.log.info("LEADER with: id = {} round = {}", node.id, node.round, node.neighbor)
          nodeRep(newState)
      else
        // Το μήνυμα δεν είναι δικό του
        if msg.id > node.id then
          // Στέλνει το μήνυμα (id, round, hop + 1, sameIdFound)
          val newMsg = msg.copy(hop = msg.hop + 1)
          node.neighbor ! newMsg

          // Ο κόμβος γίνεται passive
          val newState = node.copy(state = IRNState.Passive)
          nodeRep(newState)
        else if msg.id == node.id then
          // Ο κόμβος στέλνει το μήνυμα (κ, i, h+1, true) στον επόμενο
          val newMsg = msg.copy(hop = msg.hop + 1, sameIdFound = true)
          node.neighbor ! newMsg
          Behaviors.same
        else
          Behaviors.same
}
