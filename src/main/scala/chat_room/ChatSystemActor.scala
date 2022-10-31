package chat_room

import akka.NotUsed
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors

object ChatSystemActor {

  def main(args: Array[String]): Unit =
    ActorSystem(ChatSystemActor(), "ChatRoomDemo")

  def apply(): Behavior[NotUsed] =
    Behaviors.setup { context =>
      val chatRoomActor = context.spawn(ChatRoomActor(), "chatroom")
      val talkerActor   = context.spawn(TalkerActor(), "client")
      context.watch(talkerActor)
      chatRoomActor ! ChatRoomActor.GetSession("ol' talker", talkerActor)

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          Behaviors.stopped
      }
    }
}
