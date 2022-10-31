package protocol_2

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import protocol_2.RingProtocol.{PathMsg, Token}

import scala.annotation.tailrec

object StartEndNode {

  // Κατα την αρχικοποίηση του θα δημιουργείται(spawn) ένα πλήθος(Ν) PathNode actors,
  // οπου στον καθένα απο αυτούς θα δίνεται ένα actorRef στο επόμενο PathNode μέχρις
  // ώτου η διαδικασία φτάσει στον Ν οστό PathNode, όπου και θα δίνεται ref στον StartEndNode
  // ώστε να ολοκληρώνεται η δομή του "δαχτυλιδιού".
  def apply(text: String, nodesInRing: Int): Behavior[PathMsg] = {

    //  Βοηθητική μέθοδος για την εύκολη δημιουργία του δικτύου κόμβων.
    //    Η κατασκευή του δακτυλίου γίνεται απο το τέλος προς την αρχή
    //    Κάθε νεός κόμβος που δημιουργείται δέχεται σαν όρισμα(παραλήπτη)
    //    τον προηγούμενο κόμβο που δημιουργήθηκε.
    // 1η επανάληψη
    //  currNode = startEndNode, nodesLeft = N
    //  Επιστρέφει ref σε pathNode_N, που έχει ref σε startEndNode
    // 2η επανάληψη
    //  currNode = pathNode_N, nodesLeft = N-1
    //  Επιστρέφει ref σε pathNode_N-1, που ref σε σε pathNode_N
    // ...
    // N-οστη επανάληψη
    //  currNode = pathNode_1, nodesLeft = 0
    //  Επιστρέφει ref σε pathNode_1, που έχει ref σε σε pathNode_2
    @tailrec
    def createPath(currNode: ActorRef[PathMsg], nodesLeft: Int, ctx: ActorContext[PathMsg]): ActorRef[PathMsg] =
      if nodesLeft == 0 then
        currNode
      else
        val nextNode = ctx.spawn(PathNode(currNode), s"PathNode-No.$nodesLeft")
        createPath(nextNode, nodesLeft - 1, ctx)

    // Ορισμός λειτουργίας του κόμβου StartEndNode
    Behaviors.setup { context =>
      // Με τη χρήση της παραπάνω μεθόδου δημιουργούνται οι κόμβοι του δακτυλίου
      // και επιστρέφεται ενα actorRef στο οποίο θα σταλεί το πρώτο token απο
      // τον StartEndNode
      val firstNode = createPath(context.self, nodesInRing, context)

      // Εκκίνηση διαδικασίας στέλνοντας μήνυμα στον πρώτο κόμβο του δικτύου
      context.log.info("BEGIN {")
      firstNode ! RingProtocol.Token(text, context.self, context.self)

      // Επιστροφή ενος PathNode με επόπενο κόμβο τον firstNode 
      // ολοκληρώνοντας έτσι την δομή του δακτυλίου
      PathNode(firstNode)
    }
  }

}