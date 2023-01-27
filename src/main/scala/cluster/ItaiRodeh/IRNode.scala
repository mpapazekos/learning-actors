package cluster.ItaiRodeh

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable

import scala.util.Random

object IRNode {

  // Αντικείμενο που χρησιμοποείται για την αναγνώριση της υπηρεσίας
  val IRprotServiceKey: ServiceKey[IRMsg] =
    ServiceKey[IRMsg]("itai_rodeh_service")

  // Γενικός τύπος αποδεκτών μηνυμάτων για το συγκεκριμένο πρωτόκολλο
  sealed trait IRMsg extends CborSerializable

  // Οταν ληφθεί ξεκινάει έναν νεο γύρο εκλογών στέλνοντας
  // το id του στον επόμενο κόμβο
  case object StartNewElection extends IRMsg

  // Μήνυμα που εκφράζει την επικοινωνία μεταξύ κόμβων στο πρωτόκολλο των Itai και Rodeh
  private final case class RoundMsg(id: Int, round: Int, hop: Int, sameIdFound: Boolean) extends IRMsg

  // Χρησιμοποιείται για την σύνδεση μεταξύ κόμβων
  final case class Connect(forwardTo: ActorRef[IRMsg]) extends IRMsg

  // Η κάσταση συμμετοχής στο τρέχων πρωτόκολλο μπορεί να
  // παρει μια απο τις εξής τιμές
  // Passive(μη-συμμετεχων)
  // Active (συμμετεχων)
  // Leader (αρχηγός)
  enum ElectionState:
    case Passive, Active, Leader

  // Ο κόμβος θα πρεπει να διατηρει τις εξής πληροφορίες
  // totalNodes     - το πλήθος των κόμβων στο δίκτυο
  // nextNode       - Τον επόμενο κόμβο που θα προωθεί το μήνυμα
  // id             - μια τυχαία τιμή ως αναγνωριστικό
  // round          - ο αριθμός του τρέχοντος εκλογικού γύρου
  // electionState  - H τρέχουσα κατάσταση συμμετοχής του κόμβου
  // Οι πληροφορίες αυτές ενθυλακώνονται στην παρακάτω κλάση
  // για διευκόλυνση στον ορισμό νεας συμπεριφοράς
  private final case class NodeState(
                                      id: Int, totalNodes: Int,
                                      round: Int, electionState: ElectionState,
                                      nextNode: ActorRef[IRMsg]
                                    )

  def apply(totalNodes: Int): Behavior[IRMsg] =
    Behaviors.setup { context =>

      val receptionist = context.system.receptionist
      context.log.info("Registering myself with the receptionist")
      receptionist ! Receptionist.Register(IRprotServiceKey, context.self)

      Behaviors.receiveMessage {
        case Connect(forwardTo) =>
          val id = Random.between(1, totalNodes) + 1
          context.log.info("ΑΡΧΙΚΟ ID {} | ΣΥΝΔΕΔΕΜΕΝΟΣ ΜΕ {} ", id, forwardTo)
          nodeBehavior(NodeState(id, totalNodes, 1, ElectionState.Active, forwardTo))
        case _ =>
          Behaviors.same
      }
    }
  
  // Συμπεριφορά του actor που εκφράζει έναν κόμβο στο πρωτόκολλο Itai & Rodeh
  // Διατηρεί ως κατάσταση ένα αντικείμενο τύπου NodeState
  private def nodeBehavior(state: NodeState): Behavior[IRMsg] =
    Behaviors.receive { (context, message) =>
      message match {
        case StartNewElection =>
          context.log.info("ΕΚΚΙΝΗΣΗ ΝΕΩΝ ΕΚΛΟΓΩΝ ID: {}, ROUND: {} ", state.id, state.round)
          state.nextNode ! RoundMsg(state.id, state.round, 1, false)
          Behaviors.same
        case msg @ RoundMsg(id, round, hop, sameIdFound) =>
          context.log.info("ΝΕΟ ΜΗΝΥΜΑ ID: {}, ROUND: {} HOPS: {}, SameIdFound: {}", id, round, hop, sameIdFound)
          state.electionState match {
            case ElectionState.Passive =>
              context.log.info("  ΜΗ-ΣΥΜΜΕΤΕΧΩΝ. ΠΡΟΩΘΕΙΤΑΙ ΣΤΟΝ {}", state.nextNode)
              state.nextNode ! RoundMsg(id, round, hop + 1, sameIdFound)
              Behaviors.same
            case ElectionState.Active =>
              // Καλεί την αντίστοιχη μέθοδο για διαχείριση της περίπτωσης
              // με δεδομένα το μήνυμα, την κατάσταση και το πλαίσιο εκτέλεσης
              onActive(msg, state, context)
            case ElectionState.Leader =>
              // Εαν είναι αρχηγός διατηρεί τη ίδια συμπεριφορά
              Behaviors.same
          }
      }
    }

  //Κάθε ενεργός κόμβος με τη λήψη του μηνύματος  (id, round, hop, sameIdFound) κάνει τα παρακάτω:
  private def onActive(msg: RoundMsg, node: NodeState, context: ActorContext[IRMsg]): Behavior[IRMsg] =

   // To μήνυμα αφορά νέο γύρο εκλογών οπότε δεν ενδιαφέρει τον τρέχοντα κόμβο.
   // Το μήνυμα προωθείται
    if msg.round > node.round then
      context.log.info("  ΜΗΝΥΜΑ ΑΠΟ ΕΠΟΜΕΝΟ ΓΥΡΟ. ΠΡΟΩΘΕΙΤΑΙ ΣΤΟΝ {}", node.nextNode)
      val newMsg = msg.copy(hop = msg.hop + 1)
      node.nextNode ! newMsg
      Behaviors.same
    else
     // Το μήνυμα έχει κάνει hops ίδιο με το totalNodes που γνωρίζει ο κόμβος
     // αρα πρόκειται για το αρχικό μήνυμα που εστειλε και περασε απο όλους τους κόμβους
      if msg.hop == node.totalNodes then
        context.log.info("  ΤΟ ΑΡΧΙΚΟ ΜΗΝΥΜΑ ΠΕΡΑΣΕ ΑΠΟ ΟΛΟΥΣ ΤΟΥΣ ΚΟΜΒΟΥΣ")

        if msg.sameIdFound then
          context.log.info("    ΒΡΕΘΗΚΕ ΙΔΙΟ ID")
          context.self ! StartNewElection

          val nextId = Random.between(1, node.totalNodes) + 1
          val nextRound = node.round + 1
          val newState = node.copy(id = nextId, round = nextRound)

          context.log.info("    ΝΕΟ ID = {}", nextId)
          nodeBehavior(newState)
        else
          context.log.info("    ΑΡΧΗΓΟΣ ΜΕ ID = {} ROUND = {}", node.id, node.round, node.nextNode)
          val newState = node.copy(electionState = ElectionState.Leader)
          nodeBehavior(newState)
      else
        context.log.info("  ΣΥΓΚΡΙΣΗ ID: ΜΗΝΥΜΑΤΟΣ = {} | ΚΟΜΒΟΥ = {}", msg.id , node.id )
        if msg.id > node.id then

          context.log.info("    ΠΡΟΩΘΕΙΤΑΙ ΣΤΟΝ {}, ΑΛΛΑΓΗ ΚΑΤΑΣΤΑΣΗ ΣΕ ΜΗ-ΣΥΜΜΕΤΕΧΩΝ", node.nextNode)
          // Στέλνει το μήνυμα (id, round, hop + 1, sameIdFound)
          val newMsg = msg.copy(hop = msg.hop + 1)
          node.nextNode ! newMsg

          // Ο κόμβος γίνεται passive
          val newState = node.copy(electionState = ElectionState.Passive)
          nodeBehavior(newState)
        else if msg.id == node.id then

          context.log.info("    ΒΡΕΘΗΚΕ ΙΔΙΟ ID. ΠΡΟΩΘΕΙΤΑΙ ΣΤΟΝ {}", node.nextNode)
          val newMsg = msg.copy(hop = msg.hop + 1, sameIdFound = true)
          node.nextNode ! newMsg
          Behaviors.same
        else
          context.log.info("    ΚΑΤΑΣΤΡΟΦΗ ΜΗΝΥΜΑΤΟΣ")
          Behaviors.same


  def connectAndStart(actorRefs: List[ActorRef[IRMsg]]): Unit =
    createRingConnection(actorRefs) // δημιούργησε τον σύνδεσμο δακτυλίου
    actorRefs.foreach(_ ! StartNewElection)

  private def createRingConnection(actorRefs: List[ActorRef[IRMsg]]): Unit =
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
