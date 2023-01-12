package cluster

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

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
  def apply(init: Boolean, forwardTo: Option[ActorRef[RingMsg]]): Behavior[RingMsg] =
    Behaviors.setup { context =>

      val receptionist = context.system.receptionist
      context.log.info("Registering myself with the receptionist")
      receptionist ! Receptionist.Register(RingProtServiceKey, context.self)

      ringNodeBehavior(init, forwardTo)
    }

  private def ringNodeBehavior(init: Boolean, forwardTo: Option[ActorRef[RingMsg]]): Behavior[RingMsg] =
    Behaviors.receive { (context, message) =>
      message match {
        case Connect(forwardTo) =>
          RingNode(init, Some(forwardTo))

        case Token(txt, sender) =>
          context.log.info("RECEIVED TOKEN FROM {}", sender)
          if init then
            context.log.info("PROTOCOL COMPLETE {}", txt)
            RingNode(false, forwardTo)
          else
            if forwardTo.nonEmpty then
              val forwardToRef = forwardTo.get
              context.log.info("FORWARDING TO {}", forwardToRef)
              forwardToRef ! Token(txt, context.self)
            Behaviors.same

        case Init(tkn) =>
          if forwardTo.nonEmpty then
            val forwardToRef = forwardTo.get
            context.log.info("SENDING FIRST TOKEN TO {}", forwardToRef)
            forwardToRef ! Token(tkn, context.self)
            RingNode(true, forwardTo)
          else
            Behaviors.same
      }
    }
}