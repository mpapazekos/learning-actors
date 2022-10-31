package protocol_2

import akka.actor.typed.{ActorRef, ActorSystem}

object RingProtocol {

  // Ο αλγόριθμος
  // Κώδικας για τον αρχικοποιητή:
  //	  begin send<token> to nextp;
  //	  receive<token>;
  //	  decide;
  //	  end;
  //  Κώδικας για τον μη-αρχικοποιητή:
  //	  begin receive<token>;
  //	  send<token>to nextp;
  //	  end;
  // *To πρωτόκολλο ολοκληρώνεται όταν τη μήνυμα φτάνει στον αρχικοποιητή */

  // Για την υλοποίηση του πρωτοκόλλου με actors:
  //  1 actor(StartEndNode) στο ρόλο του αρχικοποιητή κόμβου
  //  Ν actors(PathNode) με λειτουργία μη-αρχικοποιητών κόμβων
 
  // Πρωτόκολλο επικοινωνίας κόμβων
  sealed trait PathMsg
  final case class Token(text: String, replyTo: ActorRef[PathMsg], sentBy: ActorRef[PathMsg]) extends PathMsg

  // Στο ActorSystem της εφαρμογής θα δίνεται ως user/guardian actor ο StartEndNode
  def main(args: Array[String]): Unit =
    ActorSystem(StartEndNode(text = "'top_secret'", nodesInRing = 10), "StartEndNode")
  
}
