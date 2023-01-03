package protocol_1

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import concurrent.duration.DurationInt

// Πρωτόκολλο επικοινωνίας του Incrementer
object Incrementer {

  // Είδος μηνυμάτων που αναγνωρίζει
  sealed trait IncMsg

  // Μήνυμα που όταν ληθφεί θα αυξηθεί
  // η τρεχουσα τιμή που διατηρειται στον actor κατά 1
  case object PlusOne extends IncMsg
  case object Begin extends IncMsg

  // Συνάρτηση που αρχικοποιεί τη συμπεριφορά του actor
  // Ως παράμετροί δίνονται η αρχική τιμή που διατηρεί(init)
  // και η αναφορά στον actor που θα την εκτυπώνει(sendTo).
  def apply(init: Int, sendTo: ActorRef[Printer.PrintMsg]): Behavior[IncMsg] =
    Behaviors.setup{ ctx =>
      new Incrementer(ctx, sendTo).plus(init)
    }
}

class Incrementer(ctx: ActorContext[Incrementer.IncMsg], sendTo: ActorRef[Printer.PrintMsg]) {
  import Incrementer._

  // Ορίζει μια συμπεριφορά που διατηρεί κάθε στιγμή μια ακέραια τιμή curr.
  // Η τιμή μπορεί να αλλάξει με τα μηνύματα που επεξεργάζονται.
  private def plus(curr: Int): Behavior[IncMsg] =
    Behaviors.receiveMessage {
      // Σε περιπτωση μηνύματος PlusOne
      case PlusOne =>
        // Αύξησε τον τρέχον αριθμό κατα 1
        val nextNum = curr + 1
        // Στείλε ένα μήνυμα στον Printer(sendTo) με τον καινούργιο αριθμό για εκτύπωση
        sendTo ! Printer.PrintNextNumber(nextNum, ctx.self)
        // Επιστρέφεται μια συμπεριφορά με την
        plus(nextNum)
      case Begin =>
        sendTo ! Printer.PrintNextNumber(curr, ctx.self)
        Behaviors.same
    }
}
