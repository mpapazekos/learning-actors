package cluster.Echo

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable

object EchoNode {

  val EchoProtServiceKey: ServiceKey[EchoMsg] =
    ServiceKey[EchoMsg]("echo_node_service")

  sealed trait EchoMsg extends CborSerializable

  // Σύμφωνα με το πρωτόκολλο κάθε actor που αντιπροσωπεύει έναν κόμβο
  // θα δέχεται τουλαχιστον ένα μήνυμα τύπου Token με πληροφορίες για τον αποστολέα
  final case class Token(sender: ActorRef[EchoMsg]) extends EchoMsg
  final case class TokenResponse(sender: ActorRef[EchoMsg]) extends EchoMsg

  // Επιπλέον θα χρειαστεί ένα μήνυμα που θα τον ειδοποιεί
  // να ξεκινήσει τη διαδικασία, καθιστώντας τον αρχικοποιητή
  case object Begin extends EchoMsg

  // Καθώς και ένα μήνυμα πληροφορώντας τον με
  // τους κόμβους γείτονες που θα επικοινωνεί
  final case class Connect(neighbors: Set[ActorRef[EchoMsg]]) extends EchoMsg

  // Αρχικοποίηση συμπεριφοράς
  // Γινεται εγγραφή στον receptionist
  // και επιστρέφεται μια συμπεριφορά που περιμένει
  // να λάβει αναφορές σε γειτονικούς actors
  def apply(): Behavior[EchoMsg] =
    Behaviors.setup { context =>

      context.log.info("Registering myself with the receptionist")
      context.system.receptionist ! Receptionist.Register(EchoProtServiceKey, context.self)

      Behaviors.receiveMessagePartial {
        case Connect(neighbors) =>
          context.log.info("CONNECTED WITH {}", neighbors.map(address).mkString(" - "))
          node(neighbors)
      }
    }


  private def node(neighbors: Set[ActorRef[EchoMsg]]): Behavior[EchoMsg] =
    Behaviors.receive { (context, message) =>
      message match {
        case Token(father) =>
          // Όταν λάβουν το πρώτο Token
          context.log.info("FIRST TOKEN RECEIVED FROM {}", address(father))

          // Αναμεταδίδουν το token σε όλους τους γείτονες, εκτός του πατέρα τους.
          neighbors.foreach { n =>
            if !(n equals father) then
             context.log.info("SENDING TOKEN TO {}", address(n))
             n ! Token(context.self)
          }

          // «Σημειώνουν» τον αποστολέα ως πατέρα τους
          // και συνεχίζουν τη λειτουργία τους.
          context.log.info("WAITING FOR RESPONSES")
          nonInitializer(neighbors - father, Set.empty, father)
        case Begin =>
          //Ο αρχικοποιητής:
          context.log.info("INITIALIZER")

          // - Στέλνει σε όλους τους γείτονες το μήνυμα token.
          // - Περιμένει να λάβει από όλους τους γείτονες το μήνυμα token.
          neighbors.foreach { n =>
            context.log.info("SENDING TOKEN TO {}", address(n))
            n ! Token(context.self)
          }
          // Αλλαγή συμπεριφορά σε actor αρχικοποιητή
          initializer(neighbors, Set.empty)
        case _ =>
          Behaviors.same
      }
    }

  private def nonInitializer(
                              neighbors: Set[ActorRef[EchoMsg]],
                              responded: Set[ActorRef[EchoMsg]],
                              father: ActorRef[EchoMsg]
                            ): Behavior[EchoMsg] =
    Behaviors.receive { (context, message) =>
      message match
        case Token(sender) =>
          context.log.info("TOKEN RECEIVED FROM {}", address(sender))
          // Όταν λάβουν οποιοδήποτε αλλο Token απλά απαντούν στον αποστολέα
          sender ! TokenResponse(context.self)
          Behaviors.same
        case TokenResponse(sender) =>
          context.log.info("{} RESPONDED ", address(sender))
          val respondedUpd = responded + sender
          context.log.info("PENDING {} ", (neighbors -- respondedUpd).map(address).mkString("\n"))
          if neighbors equals respondedUpd then
            context.log.info("EVERYONE RESPONDED! REPLYING TO FATHER")
            father ! TokenResponse(context.self)
            node(neighbors + father)
          else
            nonInitializer(neighbors, respondedUpd, father)
        case _ =>
          Behaviors.same
    }

  private def initializer(neighbors: Set[ActorRef[EchoMsg]], responded: Set[ActorRef[EchoMsg]]): Behavior[EchoMsg] =
    Behaviors.receive { (context, message) =>
      message match
        case TokenResponse(sender) =>
          context.log.info("{} RESPONDED ", address(sender))
          val respondedUpd = responded + sender
          context.log.info("PENDING {} ", (neighbors -- respondedUpd).map(address).mkString("\n").toString)
          if neighbors equals respondedUpd then
            context.log.info("PROTOCOL COMPLETE")
            node(neighbors)
          else
            initializer(neighbors, respondedUpd)
        case _ =>
          Behaviors.same
    }

  // Δεδομένης μια αναφοράς σε actor, επιστρέφει
  // ενα αλφαριθμητικό με τη διέυθυνση συστήματός του
  private def address[T](actorRef: ActorRef[T]): String =
    actorRef.path.address.toString

  
  /**
    * Χρησιμοποιείται μόνο για ένα παράδειγμα
    * Συνδέει 5 κόμβους με μια συγκεκριμένη τοπολογία:
    *
    *   [0]---------[1]
    *    | \       /
    *    |  \     /
    *    |   \   /
    *    |    [2]
    *    |       \
    *    |        \
    *   [4]--------[3]
    * 
    */
  def connectNodes(actorRefs: List[ActorRef[EchoMsg]]): Unit =
    require(actorRefs.size == 5)

    actorRefs(0) ! Connect(Set(actorRefs(1), actorRefs(2), actorRefs(4)))
    actorRefs(1) ! Connect(Set(actorRefs(0), actorRefs(2)))
    actorRefs(2) ! Connect(Set(actorRefs(0), actorRefs(1), actorRefs(3)))
    actorRefs(3) ! Connect(Set(actorRefs(2), actorRefs(4)))
    actorRefs(4) ! Connect(Set(actorRefs(0), actorRefs(3)))

    actorRefs(0) ! Begin
}