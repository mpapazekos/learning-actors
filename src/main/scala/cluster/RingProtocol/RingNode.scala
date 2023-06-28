package cluster.RingProtocol

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable

object RingNode {

  // Αντικείμενο που χρησιμοποείται για την αναγνώριση της υπηρεσίας
  val RingProtServiceKey: ServiceKey[RingMsg] =
    ServiceKey[RingMsg]("ring_protocol_service")

  // Τα μηνύματα που αποδέχεται είναι τύπου RingMsg
  sealed trait RingMsg extends CborSerializable

  // Οταν δεχθεί ενα αντικείμενο Init μαρκάρει τον ευαυτό του ως αρχικοποιητη
  // και ξεκινάει τη διαδικασία του πρωτοκόλλου
  final case class Init(token: String) extends RingMsg

  // Οταν ληφθεί μήνυμα Token με το κείμενο και τον αποστολέα
  // Εαν είναι αρχικοποιητής τερματίζει τη διαδικασία
  // διαφορετικά προωθεί το μήνυμα στον επόμενο
  final case class Token(txt: String, sender: ActorRef[RingMsg]) extends RingMsg

  // Οταν ληφθεί μήνυμα Connect
  // ο κόμβος που θα προωθεί τα μηνύματα παίρνει καινούργια τιμή
  final case class Connect(forwardTo: ActorRef[RingMsg]) extends RingMsg

  // Κατα τη δημιουργία του δέχεται ως όρισματα το εαν θα είναι αρχικοποιητης
  // και τον κόμβο που θα προωθεί τα μηνύματα. Οι τιμές αυτές αλλάζουν ανάλογα
  // τα μηνύματα που λαμβάνει
  def apply(): Behavior[RingMsg] =
    Behaviors.setup { context =>

      val receptionist = context.system.receptionist
      context.log.info("ΔΗΛΩΣΗ ΥΠΗΡΕΣΙΑΣ ΣΤΟΝ RECEPTIONIST")
      receptionist ! Receptionist.Register(RingProtServiceKey, context.self)

      // Περιμένει να συνδεθεί με τον γειτονικό κόμβο
      // στο δικτυο στον οποίο θα προωθεί ένα μήνυμα
      Behaviors.receiveMessage {
        case Connect(forwardTo) =>
          // Με το αποκτήσει τη πληροφορία
          // αλλάζει στην κύρια συμπεριφορά
          // του πρωτοκόλλου
          ringNodeBehavior(false, forwardTo)
        case _ =>
          // Εαν δεν έχει ορισμένο γειτονικό κόμβο
          // απλά συνεχίζει να περιμένει
          Behaviors.same
      }
    }

  private def ringNodeBehavior(init: Boolean, forwardTo: ActorRef[RingMsg]): Behavior[RingMsg] =
    Behaviors.receive { (context, message) =>
      message match {
        case Token(txt, sender) =>
          context.log.info("ΛΗΨΗ ΤΟΚΕΝ ΑΠΟ {}", sender)
          if init then
            context.log.info("ΟΛΟΚΛΗΡΩΣΗ ΠΡΩΤΟΚΟΛΛΟΥ {}", txt)
            ringNodeBehavior(false, forwardTo)
          else
            context.log.info("ΠΡΟΩΘΗΣΗ ΣΕ {}", forwardTo)
            forwardTo ! Token(txt, context.self)
            Behaviors.same

        case Init(tkn) =>
          context.log.info("ΑΠΟΣΤΟΛΗ ΠΡΩΤΟΥ ΤΟΚΕΝ ΣΕ {}", forwardTo)
          forwardTo ! Token(tkn, context.self)
          ringNodeBehavior(true, forwardTo)
      }
    }

  def connectAndStart(actorRefs: List[ActorRef[RingMsg]]): Unit =
    createRingConnection(actorRefs) // δημιούργησε τον σύνδεσμο δακτυλίου
    actorRefs.foreach(_ ! Init("token_msg"))
  
  private def createRingConnection(actorRefs: List[ActorRef[RingMsg]]): Unit =
  
    // Εαν
    //   actorRefs = [ref1, ref2, ref3, ... , refN]
    // Τότε
    //   headToLast = [ref2, ref3, ... , refN, ref1]
    val headToLast = actorRefs.tail.appended(actorRefs.head)
  
    // zipped = [(ref1, ref2), (re2, ref3), ... , (refN-1, refN), (refN-1, ref1)]
    val zipped = actorRefs.zip(headToLast)
  
    // Δημιουργία δακτυλίου
    zipped.foreach((ref1, ref2) => ref1 ! Connect(ref2))
}