package cluster.ChangRoberts

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable

object CRNode {

  // Αντικείμενο που χρησιμοποείται για την αναγνώριση της υπηρεσίας
  val CRProtServiceKey: ServiceKey[CRMsg] =
    ServiceKey[CRMsg]("chang_roberts_service")

  // Συμφωνα με το πρωτόκολλο θα πρέπει να δέχεται τουλάχιστον
  // δυο διαφορετικά είδη μηνυμάτων. Ενα με τον αριθμό του προηγούμενου
  // κόμβου και ένα με το αποτέλεσμα της εκλογής
  sealed trait CRMsg extends CborSerializable
  final case class ElectionMessage(senderId: Int, sender: ActorRef[CRMsg]) extends CRMsg
  final case class Elected(leaderId: Int, leader: ActorRef[CRMsg]) extends CRMsg

  // Μήνυμα που χρησιμοποιείται μόνο για την
  // εκκίνηση της διαδικασίας.
  // όλις ληφθεί, αποστέλνεται μήνυμα στον επόμενο κόμβο.
  case object Begin extends CRMsg

  // Οταν ληφθεί μήνυμα Connect
  // ο κόμβος που θα προωθεί τα μηνύματα παίρνει καινούργια τιμή
  final case class Connect(forwardTo: ActorRef[CRMsg]) extends CRMsg


  def apply(id: Int): Behavior[CRMsg] =
    Behaviors.setup { context =>

      val receptionist = context.system.receptionist
      context.log.info("Registering myself with the receptionist")
      receptionist ! Receptionist.Register(CRProtServiceKey, context.self)

      nodeBehavior(id, false, None)
    }


  // Η κατάσταση που θα διατηρεί ο συγκεκριμένος actor
  //  id - το μοναδικό αναγνωριστικό του κόμβου (δίνεται απο τον χρήστη)
  //  participant - εαν είναι συμμετέχων ή όχι
  //  forwardTo - ο κόμβος που θα προωθεί το μήνυμα
  private def nodeBehavior(id: Int, participant: Boolean, forwardTo: Option[ActorRef[CRMsg]]): Behavior[CRMsg] =
    Behaviors.receive{ (context, message) =>
      message match {
        case Connect(forwardTo) =>
          nodeBehavior(id, participant, Some(forwardTo))
        case Begin =>
          // Εάν έχει οριστεί επόμενος κόμβος
          if forwardTo.isDefined then
            context.log.info("ΕΚΚΙΝΗΣΗ ΔΙΑΔΙΚΑΣΙΑΣ ΕΚΛΟΓΗΣ")
            forwardTo.get ! ElectionMessage(id, context.self)
            nodeBehavior(id, true, forwardTo)
          else
            Behaviors.same
        case ElectionMessage(senderId, sender) =>
          context.log.info("ΛΗΦΘΗΚΕ ΤΙΜΗ {} ΑΠΟ {}", senderId, sender)
          // Εάν έχει οριστεί επόμενος κόμβος
          if forwardTo.isDefined then
            val nextNode = forwardTo.get
            if senderId > id then
              nextNode ! ElectionMessage(senderId, context.self)
              nodeBehavior(id, true, forwardTo)
            else if senderId < id then
              if participant then
                Behaviors.same
              else
                nextNode ! ElectionMessage(id, context.self)
                nodeBehavior(id, true, forwardTo)
            else
              context.log.info("ΕΠΙΛΟΓΗ ΑΡΧΗΓΟΥ")
              nextNode ! Elected(id, context.self)
              nodeBehavior(id, false, forwardTo)
          else
            Behaviors.same
        case Elected(leaderId, leader) =>
          if forwardTo.isDefined then
            context.log.info("ΟΡΙΣΜΟΣ ΑΡΧΗΓΟΥ {} ΜΕ ΤΙΜΗ {}", leader, leaderId)
            forwardTo.get ! Elected(id, context.self)
            nodeBehavior(id, false, forwardTo)
          else
            Behaviors.same
      }
    }



}
