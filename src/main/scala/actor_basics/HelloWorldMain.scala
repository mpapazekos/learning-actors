package actor_basics

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors


object HelloWorldMain {
  final case class SayHello(name: String)

  def apply(): Behavior[SayHello] =
    Behaviors setup { context =>
        val greeterActor =
          context.spawn(HelloWorld(), "greeter")

        Behaviors receiveMessage { sayHelloMsg =>
          val replyTo = context.spawn(HelloWorldBot(max = 3), sayHelloMsg.name)
          greeterActor ! HelloWorld.Greet(sayHelloMsg.name, replyTo)
          Behaviors.same
        }
    }
}
