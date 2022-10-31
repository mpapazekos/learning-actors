package protocol_2

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import protocol_2.RingProtocol.{PathMsg, Token}

object PathNode {
  
  def apply(sendTo: ActorRef[PathMsg]): Behavior[PathMsg] =
    Behaviors.receive { (context, message) =>
      message match
        case Token(text, replyTo, from) =>
          context.log.info(">> {} RECEIVED: {} from: {}", context.self.path.name, text, replyTo.path.name)

          if from eq context.self then
            // Οταν ληφθεί το token από τον τελευταίο κόμβο του δακτυλίου
            // εκτυπώνεται αντιστοιχο μήνυμα και σταματά η λειτουργία του συγκεκριμένου actor
            context.log.info("} END ")
            Behaviors.stopped
          else
            sendTo ! Token(text, context.self, from)
            Behaviors.same
    }
}