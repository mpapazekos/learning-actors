package protocol_3

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.ActorContext

import scala.annotation.tailrec
import scala.util.Random

object ChangAndRoberts {
  
  def main(args: Array[String]): Unit =
    // Δημιουργία δακτυλίου
    val ringNetwork = ActorSystem(StartNode(20), "startNode")

    // Εκκίνηση της διαδικασίας με την αποστολή
    // ενός μηνύματος για τον έλεγχο ύπαρξης αρχηγού
    // σε ένα κόμβο του δικτύου
    ringNetwork ! CheckLeader

  // Είδη καταστάσεων ενός κόμβου στον δακτύλιο κατα την εκλογή αρχηγού
  sealed trait NodeState
  case object Candidate extends NodeState
  case object NonCandidate extends NodeState

  // Πρωτόκολλο επικοινωνίας μεταξύ κόμβων στο δακτύλιο
  sealed trait RingMsg
  case object CheckLeader extends RingMsg
  final case class ElectionMessage(id: Long) extends RingMsg
  final case class Elected(leader: ActorRef[RingMsg]) extends RingMsg
  
}