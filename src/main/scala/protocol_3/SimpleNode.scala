package protocol_3

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import protocol_3.ChangAndRoberts.*

object SimpleNode {

  // Για την δημιουργία ενός SimpleNode χρειάζονται
  // ένα id,
  // neighbor: ο κόμβος που βρίσκεται δεξιά του στο δακτύλιο
  // leader: ο τρέχον αρχηγός του δικτύου
  def apply(
             id: Long,
             neighbor: ActorRef[RingMsg],
             leader: Option[ActorRef[RingMsg]]
           )
  : Behavior[RingMsg] =
    node(id, NonCandidate, neighbor, leader)


  private def node(
                   id: Long,
                   state: NodeState,
                   neighbor: ActorRef[RingMsg],
                   leader: Option[ActorRef[RingMsg]]
                  )
  : Behavior[RingMsg] =

    Behaviors.receive { (context, message) =>
      message match
        case ElectionMessage(prevNodeId) =>
          context.log.info("{} received ElectionMessage with EnemyNodeId: {}", context.self.path.name, prevNodeId)
          id compareTo prevNodeId match
            case 0 =>
              // ταυτότητα == ληφθείσα ταυτότητα
              context.log.info("|--> DRAW { {} == {} }", id, prevNodeId)
              neighbor ! Elected(context.self, id)

              // Ως καινούργια συμπεριφορά, ο συγκεκριμένος actor
              // είναι πλέον NonCandidate και αναβαθμίζει την τιμή
              // του leader με το actorRef στον εαυτό του
              node(id, NonCandidate, neighbor, Some(context.self))
            case res if res > 0 =>
              // ταυτότητα < ληφθείσα ταυτότητα
              context.log.info("|--> LOSE { {} < {} }", id, prevNodeId)
              neighbor ! ElectionMessage(prevNodeId)

              // Το μόνο που αλλάζει στη καινούργια συμπεριφορά του actor
              // είναι η κατάστασή του σε Candidate
              node(id, Candidate, neighbor, leader)
            case res if res < 0 =>
              // ταυτότητα > ληφθείσα ταυτότητα

              // Εαν συμμετέχει στις εκλογές
              if state == Candidate then
                Behaviors.same
              else
                context.log.info("|--> WIN { {} > {} }", id, prevNodeId)
                neighbor ! ElectionMessage(id)
                node(id, Candidate, neighbor, leader)

        // Μήνυμα που περιέχει το actorRef του νέου κόμβου αρχηγού
        case Elected(newLeader, leaderId) =>
          context.log.info("{} received Elected message with new leader: {}", context.self.path.name, newLeader.path.name)

          // Εαν το μήνυμα με τον καινούργιο αρχηγό δεν αναφέρεται στον εαυτό του
          if newLeader.ne(context.self) then
            // Το προωθεί στον επόμενο κόμβο
            neighbor ! Elected(newLeader, leaderId)
            // Ενημερώνει την κατάστασή του σε NonCandidate
            // και την τιμή του τρέχοντος αρχηγού σε αυτή που λήφθηκε
            node(id, NonCandidate, neighbor, Some(newLeader))
          else
            // Διαφορετικά η διαδικασία της εκλογής ολοκληρώθηκε
            context.log.info("LEADER FINALIZED: {}", context.self.path.name)
            Behaviors.same
        // Μήνυμα που ξεκινά τη διαδικασία ελέγχου ύπαρξης ενός αρχηγού
        case CheckLeader =>
          context.log.info("{} STARTING ELECTION", context.self.path.name)
          neighbor ! ElectionMessage(id)
          node(id, Candidate, neighbor, leader)

    }
}
