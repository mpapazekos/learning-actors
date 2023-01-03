package protocol_1

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

//Πρωτόκολλο επικοινωνίας του Printer
object Printer {

  // Είδος μηνυμάτων που αναγνωρίζει
  sealed trait PrintMsg

  // Μήνυμα με τον αριθμό προς εκτύπωση
  final case class PrintNextNumber(num: Int, replyTo: ActorRef[Incrementer.IncMsg]) extends PrintMsg

  // Η λειτουργία του είναι σχετικά απλή:
  // ακούει μόνο σε μηνύματα τύπου PrintNextNumber,
  // απο τα οποία, αφού ληφθούν,
  // εκτυπώνει τον επόμενο αριθμό
  // και στέλνει μήνυμα στον αντιστοιχο Incrementer
  def apply(): Behavior[PrintMsg] =
    Behaviors.receive { (context, msg) =>
      msg match
        // Σε περίπτωση που ληθφεί μήνυμα PrintNextNumber
        case PrintNextNumber(num, replyTo) =>
          // Εκτύπωση αριθμου που λήφθηκε
          context.log.info(">> {} PRINTING # {}", context.self.path.toString, num)
          // Αποστολή μηνύματος PlusOne για αυξηση του τρέχοντος αριθμού
          replyTo ! Incrementer.PlusOne
          // Συνέχισε να λειτουργείς με τον ίδιο τρόπο
          Behaviors.same
    }
}
