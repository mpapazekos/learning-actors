package actor_basics

import akka.actor.typed.ActorSystem

object ActorBasics extends App {
  val system = 
    ActorSystem(HelloWorldMain(), "hello")
  
  system ! HelloWorldMain.SayHello("World")
  system ! HelloWorldMain.SayHello("Akka")
}
