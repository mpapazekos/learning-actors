package actor_basics

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

//The protocol is bundled together
// with the behavior that implements it
// in a nicely wrapped scope, the HelloWorld object.
object HelloWorld {

  //The accepted message types of an Actor
  // together with all reply types
  // defines the protocol spoken by this Actor.
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])
  
  def apply(): Behavior[Greet] =
    Behaviors receive { 
      (context, greetMsg) =>
        context.log.info("Hello {}!", greetMsg.whom)
        greetMsg.replyTo ! Greeted(greetMsg.whom, context.self)
        Behaviors.same
    }
}
