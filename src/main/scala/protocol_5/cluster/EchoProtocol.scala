package protocol_5

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object EchoProtocol {

  private object EchoNode {

    private val serviceKey = ServiceKey[Token]

    sealed trait EPMsg
    case object Init extends EPMsg
    final case class Token(replyTo: ActorRef[EPMsg]) extends EPMsg
    private case class Neighbors(listing: Receptionist.Listing) extends EPMsg

    def apply(replyTo: Option[ActorRef[EPMsg]]): Behavior[EPMsg] =
      Behaviors.setup { ctx =>
        val listingAdapter: ActorRef[Receptionist.Listing] = ctx.messageAdapter(Neighbors.apply)



        Behaviors.empty
      }
  }
  
}
