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
  // Μόλις ληφθεί, αποστέλνεται μήνυμα στον επόμενο κόμβο.
  case object Begin extends CRMsg

  // Οταν ληφθεί μήνυμα Connect
  // ο κόμβος που θα προωθεί τα μηνύματα παίρνει καινούργια τιμή
  final case class Connect(forwardTo: ActorRef[CRMsg]) extends CRMsg

  def apply(id: Int): Behavior[CRMsg] =
    Behaviors.setup { context =>

      val receptionist = context.system.receptionist
      context.log.info("Registering myself with the receptionist")
      receptionist ! Receptionist.Register(CRProtServiceKey, context.self)

      Behaviors.receiveMessage {
        case Connect(forwardTo) =>
          context.log.info("ID {} CONNECTING WITH {} ", id, forwardTo)
          nodeBehavior(id, false, forwardTo)
        case _ =>
          Behaviors.same
      }
      
    }

  // Η κατάσταση που θα διατηρεί ο συγκεκριμένος actor
  //  id - το μοναδικό αναγνωριστικό του κόμβου (δίνεται απο τον χρήστη)
  //  participant - εαν είναι συμμετέχων ή όχι
  //  forwardTo - ο κόμβος που θα προωθεί το μήνυμα
  private def nodeBehavior(id: Int, participant: Boolean, forwardTo: ActorRef[CRMsg]): Behavior[CRMsg] =
    Behaviors.receive{ (context, message) =>
      message match {
        case Begin =>
          context.log.info("ΕΚΚΙΝΗΣΗ ΔΙΑΔΙΚΑΣΙΑΣ ΕΚΛΟΓΗΣ")
          forwardTo ! ElectionMessage(id, context.self)
          nodeBehavior(id, true, forwardTo)
    
        case ElectionMessage(senderId, sender) =>
          context.log.info("ΛΗΦΘΗΚΕ ΤΙΜΗ {} ΑΠΟ {}", senderId, sender)
          if senderId > id then
            context.log.info("ΑΠΟΣΤΟΛΗ ID {} ΣΕ {}", senderId, forwardTo)
            forwardTo ! ElectionMessage(senderId, context.self)
            nodeBehavior(id, true, forwardTo)
          else if senderId < id then
            if participant then
              Behaviors.same
            else
              context.log.info("ΑΠΟΣΤΟΛΗ ID {} ΣΕ {}", id, forwardTo)
              forwardTo ! ElectionMessage(id, context.self)
              nodeBehavior(id, true, forwardTo)
          else
            context.log.info("ΕΠΙΛΟΓΗ ΑΡΧΗΓΟΥ")
            forwardTo ! Elected(id, context.self)
            nodeBehavior(id, false, forwardTo)

        case Elected(leaderId, leader) =>
          if participant then
            context.log.info("ΟΡΙΣΜΟΣ ΑΡΧΗΓΟΥ {} ΜΕ ΤΙΜΗ {}", leader, leaderId)
            forwardTo ! Elected(leaderId, context.self)
          if leaderId equals id then
            context.log.info("ΟΛΟΙ ΕΝΗΜΕΡΩΘΗΚΑΝ")
          nodeBehavior(id, false, forwardTo)

        case _ => Behaviors.same
      }
    }

  def connectAndStart(actorRefs: List[ActorRef[CRMsg]]): Unit =
    createRingConnection(actorRefs) // δημιούργησε τον σύνδεσμο δακτυλίου
    actorRefs.foreach(_ ! Begin)

  private def createRingConnection(actorRefs: List[ActorRef[CRMsg]]): Unit =

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
