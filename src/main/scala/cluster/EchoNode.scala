package cluster

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.typed.Cluster

import scala.concurrent.duration._

object EchoNode {

  private val TokenServiceKey: ServiceKey[Token] = ServiceKey[Token]("TokenService")

  sealed trait Msg

  final case class Token(info: String, replyTo: ActorRef[Token]) extends Msg with CborSerializable

  private case class NeighborsUpd(neighbors: Set[ActorRef[Token]]) extends Msg

  private case object EveryoneResponded extends Msg

  private case object Init extends Msg

  def apply(): Behavior[Msg] =
    Behaviors.setup { ctx =>

      //subscribe to the receptionist for tokenService updates
      val listingAdapter = ctx.messageAdapter[Receptionist.Listing] {
        case TokenServiceKey.Listing(neighbors) => NeighborsUpd(neighbors)
      }

      ctx.log.info("Subscribing to be updated for when new TokenService nodes appear")
      ctx.system.receptionist ! Receptionist.Subscribe(TokenServiceKey, listingAdapter)

      if Cluster(ctx.system).selfMember.hasRole("init") then
        Behaviors.withTimers { timers =>
          timers.startTimerWithFixedDelay(Init, 10.seconds)
          init(Set.empty)
        }
      else
        ctx.log.info("Registering myself with the receptionist")
        ctx.system.receptionist ! Register(TokenServiceKey, ctx.self)
        echo(None, Set.empty)
    }

  private def init(known: Set[ActorRef[Token]]): Behavior[Msg] =
    Behaviors.receivePartial { (ctx, msg) =>
      msg match
        case Init =>
          if known.nonEmpty then
            ctx.log.info("Asking every node... ")
            val waitBehavior = waiting(ctx.self, known)
            val respondTo = ctx.spawnAnonymous(waitBehavior)
            known.foreach(_ ! Token("ECHO", respondTo))
          Behaviors.same
        case NeighborsUpd(tknServices) =>
          ctx.log.info("Neighbors update: {}", tknServices.toString())
          init(tknServices)
        case EveryoneResponded =>
          ctx.log.info("EVERYONE RESPONDED")
          Behaviors.same
    }

  private def echo(reportTo: Option[ActorRef[Token]], known: Set[ActorRef[Token]]): Behavior[Msg] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case Token(info, replyTo) =>
          if reportTo.isEmpty then // first token received
            ctx.log.info("REPORTING TO: {}", replyTo)
            val pending = known -- Set(ctx.self, replyTo)
            val waitBehavior = waiting(ctx.self, pending)
            val respondTo = ctx.spawnAnonymous(waitBehavior)
            pending.foreach(_ ! Token(info, respondTo))
            echo(Some(replyTo), known)
          else
            ctx.log.info("TOKEN RECEIVED FROM: {}", replyTo)
            replyTo ! Token(info, ctx.self)
            Behaviors.same
        case NeighborsUpd(tknServices) =>
          ctx.log.info("Neighbors update: {}", tknServices.toString())
          echo(reportTo, tknServices)
        case EveryoneResponded =>
          reportTo.get ! Token("DONE", ctx.self)
          echo(None, known)
    }

  private def waiting(replyTo: ActorRef[Msg], pending: Set[ActorRef[Token]]): Behavior[Token] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case Token(info, sender) =>
          ctx.log.info("REPLY: {} From: {}", info, sender)
          val pendingUpd = pending - sender
          if pendingUpd.isEmpty then
            replyTo ! EveryoneResponded
            Behaviors.stopped
          else
            waiting(replyTo, pendingUpd)
    }
}
