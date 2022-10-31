package protocol_1

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

// Πρωτόκολλο επικοινωνίας του Incrementer
object Incrementer {

  // Είδος μηνυμάτων που αναγνωρίζει
  sealed trait IncrementMsg

  // Μήνυμα με πληροφορίες τον αριμός προς αύξηση και το actorRef ενός Printer,
  // ώστε να γνωρίζει ο Incrementer σε ποιον να στείλει τον επόμενο αριθμό
  final case class PlusOne(num: Int, replyTo: ActorRef[Printer.PrintMsg]) extends IncrementMsg

  def apply(): Behavior[IncrementMsg] =
    Behaviors receive { (context, message) =>
      message match
        // Σε περιπτωση μηνύματος PlusOne
        case PlusOne(num, replyTo) =>
          // Αύξησε τον αριθμό που έλαβες κατα 1
          val nextNum = num + 1
          // Στείλε ένα μήνυμα στον Printer(replyTo) με τον καινούργιο αριθμό
          replyTo ! Printer.PrintNextNumber(nextNum, context.self)
          // Συνέχισε να λειτουργείς με τον ίδιο τρόπο
          Behaviors.same
    }
}
