package actor_basics

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

//Receives the reply from a Greeter
// and sends a number of additional greeting messages
// and collect the replies
// until a given max number of messages have been reached
object HelloWorldBot {
  def apply(max: Int): Behavior[HelloWorld.Greeted] =
    bot(0, max)

  private def bot(greetingCounter: Int, max: Int): Behavior[HelloWorld.Greeted] =
    Behaviors receive {
      (context, greetedMsg) =>
        val n = greetingCounter + 1
        context.log.info("Greeting {} for {}", n, greetedMsg.whom)
        if n == max then
          Behaviors.stopped
        else
          greetedMsg.from ! HelloWorld.Greet(greetedMsg.whom, context.self)
          bot(n, max)
    }
}
