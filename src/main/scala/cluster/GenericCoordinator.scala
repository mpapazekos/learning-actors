package cluster

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable

object GenericCoordinator {
  
  sealed trait CrdMsg extends CborSerializable

  // Μήνυμα που εκφράζει την απάντηση του receptionist
  // περιέχει ένα σύνολο απο αναφορές
  private case class ReceptionistAnswer[T](listing: Set[ActorRef[T]]) extends CrdMsg

  def apply[T](minNodes: Int, serviceKey: ServiceKey[T], f: List[ActorRef[T]] => Unit ): Behavior[CrdMsg] =
    Behaviors.setup { context =>

      // Ειδικός actor ο οποίος μετατρέπει τις απαντήσεις του receptionist
      // σε μηνύματα που αναγνωρίζει αυτός ο actor.
      // Χρησιμοποείται ώστε ο κάθε actor να διατηρεί εύκολα
      // το δικό του πρωτόκολλο αποδεκτού τύπου μηνυμάτων
      val subscriber =
        context.messageAdapter[Receptionist.Listing] {
          case serviceKey.Listing(services) => ReceptionistAnswer(services)
        }

      // Εγγραφή στην υπηρεσία ProtService
      context.system.receptionist ! Receptionist.Subscribe(serviceKey, subscriber)

      coordinator(minNodes, List.empty, f)
    }

  private def coordinator[T](minNodes: Int, known: List[ActorRef[T]], f: List[ActorRef[T]] => Unit): Behavior[CrdMsg] =
    Behaviors.receiveMessage {
        case ReceptionistAnswer(actorRefs) =>
          val updatedRefsList = actorRefs.asInstanceOf[Set[ActorRef[T]]].toList
          if actorRefs.size < minNodes then // Εαν οι κόμβοι δεν ειναι αρκετοι
            coordinator(minNodes, updatedRefsList, f) // ενημέρωσε τη λίστα και συνέχισε να περιμένεις
          else 
            f(updatedRefsList)
            Behaviors.ignore
      }
}
