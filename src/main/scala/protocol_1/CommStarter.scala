package protocol_1

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object CommStarter {

  // Με τη δημιουργία του συγκεκριμένου actor
  // δημιουργούνται επιπλέον ο Printer και ο Incrementer
  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      val printerRef     = context.spawn(Printer(), "printer")
      val incrementerRef = context.spawn(Incrementer(0, printerRef), "incrementer")

      // Η διαδικασία ξεκινάει με την αποστολή ενός μηνύματος PrintNextNumber
      // στον Printer actor
      incrementerRef ! Incrementer.Begin
      Behaviors.empty
    }
}
