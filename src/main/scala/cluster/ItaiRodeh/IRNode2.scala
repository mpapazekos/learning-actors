package cluster.ItaiRodeh

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import cluster.CborSerializable

import scala.util.Random

object IRNode2 {

  // Αντικείμενο που χρησιμοποείται για την αναγνώριση της υπηρεσίας
  val IRprotServiceKey: ServiceKey[IRMsg] =
    ServiceKey[IRMsg]("itai_rodeh_service")

  // Γενικός τύπος αποδεκτών μηνυμάτων για το συγκεκριμένο πρωτόκολλο
  sealed trait IRMsg extends CborSerializable

  // Οταν ληφθεί ξεκινάει έναν νεο γύρο εκλογών στέλνοντας
  // το id του στον επόμενο κόμβο
  case object StartNewElection extends IRMsg

  // Χρησιμοποιείται για την σύνδεση μεταξύ κόμβων
  final case class Connect(forwardTo: ActorRef[IRMsg]) extends IRMsg

  // Μήνυμα που εκφράζει την επικοινωνία μεταξύ κόμβων στο πρωτόκολλο των Itai και Rodeh
  private final case class RoundMsg(id: Int, round: Int, hop: Int, sameIdFound: Boolean) extends IRMsg

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
                                      nextNode: Option[ActorRef[IRMsg]]
                                    )

  def apply(totalNodes: Int): Behavior[IRMsg] =
    Behaviors.setup { context =>
      
      context.log.info("Registering myself with the receptionist")
      context.system.receptionist ! Receptionist.Register(IRprotServiceKey, context.self)
      
      waitingForConnection(totalNodes)
    }

  private def waitingForConnection(totalNodes: Int): Behavior[IRMsg] =
    Behaviors.receive{  (context, message) =>
      message match {
        case Connect(forwardTo) =>
          val id = Random.between(1, totalNodes) + 1
          context.log.info("STARTING ID {} CONNECTING WITH {} ", id, forwardTo)
          readyToStart(NodeState(id, totalNodes, 1, ElectionState.Active, Some(forwardTo)))
        case _ =>
          Behaviors.same
      }
    }
      
  private def readyToStart(state: NodeState): Behavior[IRMsg] =
    Behaviors.receive { (context, message) =>
      context.log.info("STARTING_NEW_ELECTION WITH ID: {}, ROUND: {} ", state.id, state.round)
      message match {
        case StartNewElection =>
          state.nextNode.get ! RoundMsg(state.id, state.round, 1, false)
          nodeBehavior(state)
        case _ =>
          Behaviors.same
      }
    }
    
  private def nodeBehavior(state: NodeState): Behavior[IRMsg] =
    Behaviors.receive{ (context, message) =>
      context.log.info("NODE_BEHAVIOR WITH ID: {}, ROUND: {} ", state.id, state.round)
      message match 
        case msg: RoundMsg => // Λήψη μηνύματος απο διαφορετικό κόμβο
          state.electionState match {
            case ElectionState.Passive => // Σε περίπτωση που δεν συμμετέχει
              state.nextNode.get ! msg.copy(hop = msg.hop + 1)
              Behaviors.same
            case ElectionState.Active =>
              // Καλεί την αντίστοιχη μέθοδο για διαχείριση της περίπτωσης
              // με δεδομένα το μήνυμα, την κατάσταση και το πλαίσιο εκτέλεσης
              onActive(msg, state, context)
            case ElectionState.Leader =>
              // Εαν είναι αρχηγός διατηρεί τη ίδια συμπεριφορά
              Behaviors.same
          }
        case _ =>
          Behaviors.same
    }

  //Κάθε ενεργός κόμβος με τη λήψη του μηνύματος  (id, round, hop, sameIdFound) κάνει τα παρακάτω:
  private def onActive(msg: RoundMsg, state: NodeState, context: ActorContext[IRMsg]): Behavior[IRMsg] =

    // To μήνυμα αφορά νέο γύρο εκλογών οπότε δεν ενδιαφέρει τον τρέχοντα κόμβο.
    // Το μήνυμα προωθείται
    if msg.round > state.round then
      val newMsg = msg.copy(hop = msg.hop + 1)
      state.nextNode.get ! newMsg
      Behaviors.same
    else
    // Το μήνυμα έχει κάνει hops ίδιο με το totalNodes που γνωρίζει ο κόμβος
    // αρα πρόκειται για το αρχικό μήνυμα που εστειλε και περασε απο όλους τους κόμβους
      if msg.hop == state.totalNodes then
        if msg.sameIdFound then
          // έχει βρεθεί ίδιο id οπότε ξεκινάει νέο γύρο εκλογών
          context.self ! StartNewElection

          val nextId = Random.between(1, state.totalNodes) + 1
          val nextRound = state.round + 1
          val newState = state.copy(id = nextId, round = nextRound)
          context.log.info(s"Node with id ${state.id} starting new election(newId: $nextId, round: $nextRound)")
          readyToStart(newState)
        else
          context.log.info("LEADER WITH ID = {} ROUND = {}", state.id, state.round, state.nextNode.get)
          // δεν έχει βρεθεί ίδιο id οπότε γίνεται αρχηγός
          val newState = state.copy(electionState = ElectionState.Leader)
          nodeBehavior(newState)
      else
        // Το μήνυμα δεν είναι δικό του
        if msg.id > state.id then
          // Στέλνει το μήνυμα (id, round, hop + 1, sameIdFound)
          val newMsg = msg.copy(hop = msg.hop + 1)
          state.nextNode.get ! newMsg

          // Ο κόμβος γίνεται passive
          val newState = state.copy(electionState = ElectionState.Passive)
          nodeBehavior(newState)
        else if msg.id == state.id then
          // Ο κόμβος στέλνει το μήνυμα (κ, i, h+1, true) στον επόμενο
          val newMsg = msg.copy(hop = msg.hop + 1, sameIdFound = true)
          state.nextNode.get ! newMsg
          Behaviors.same
        else
          Behaviors.same
}
