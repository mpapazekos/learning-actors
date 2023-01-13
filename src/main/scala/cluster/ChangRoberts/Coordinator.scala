package cluster.ChangRoberts

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable


object Coordinator {

  import CRNode._
  // Κατασκεύη δακτυλίου μεσα στον cluster
  // ο κάθε μη-αρχικοποιητής κόμβος δηλώνει την υπηρεσία του στον receptionist
  // ενας συντονιστής κόμβος κανει εγγραφή για να γνωρίζει τις διαθεσιμες υπηρεσίες
  // και αποθηκεύει τις αναφορές στους actors που τις παρέχουν
  // με το που συλλεχθεί επαρκές πλήθος αναφορών
  // δημιουργείται ο δακτύλιος με την εξής διαδικασία
  // Για κάθε αναφορά actor στη λίστα
  // στείλε ένα μήνυμα ενημερώνοντάς τον με την αναφορά του επόμενου actor στη λίστα
  // στον οποίο και θα προωθεί το μήνυμα του πρωτοκόλλου
  // οταν επιλεχθεί ο τελευταίος actor για ενημέρωση
  // θα του αποσταλεί η αναφορά στον πρώτο
  // δημιουργώντας έτσι εναν δακτύλιο για την αποστολη μηνυμάτων


  // Τύπος αποδεκτού μηνύματος ενός σύντονιστή
  sealed trait CrdMsg extends CborSerializable

  // Μήνυμα που εκφράζει την απάντηση του receptionist
  // περιέχει ένα σύνολο απο αναφορές
  private case class ReceptionistAnswer(listing: Set[ActorRef[CRMsg]]) extends CrdMsg

  def apply(minNodes: Int): Behavior[CrdMsg] =
    Behaviors.setup { context =>

      // Ειδικός actor ο οποίος μετατρέπει τις απαντήσεις του receptionist
      // σε μηνύματα που αναγνωρίζει αυτός ο actor.
      // Χρησιμοποείται ώστε ο κάθε actor να διατηρεί εύκολα
      // το δικό του πρωτόκολλο αποδεκτού τύπου μηνυμάτων
      val subscriber =
        context.messageAdapter[Receptionist.Listing] {
          case CRProtServiceKey.Listing(services) => ReceptionistAnswer(services)
        }

      // Εγγραφή στην υπηρεσία ProtService
      context.system.receptionist ! Receptionist.Subscribe(CRProtServiceKey, subscriber)

      coordinator(minNodes, List.empty)
    }

  // Διατηρεί τον ελάχιστο αριθμό κόμβων για να ξεκινήσει η διαδικασία
  // και μια λίστα με αναφορές η οποία ανανεώνεται κάθε φορά
  // που λαμβάνεται απάντηση απο τον receptionist
  private def coordinator(minNodes: Int, known: List[ActorRef[CRMsg]]): Behavior[CrdMsg] =
    Behaviors.receive { (context, msg) =>
      msg match {
        case ReceptionistAnswer(actorRefs) =>
          val updatedRefsList = actorRefs.toList
          context.log.info(updatedRefsList.mkString("-"))
          if actorRefs.size < minNodes then // Εαν οι κόμβοι δεν ειναι αρκετοι
            coordinator(minNodes, updatedRefsList) // ενημέρωσε τη λίστα και συνέχισε να περιμένεις
          else // Διαφορετικά
            createRingConnection(updatedRefsList) // δημιούργησε τον σύνδεσμο δακτυλίου
            updatedRefsList.head ! Begin // πες στον πρώτο στη λίστα να ξεκινήσει
            Behaviors.ignore
      }
    }
      
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
