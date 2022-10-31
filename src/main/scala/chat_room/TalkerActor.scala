package chat_room

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object TalkerActor{
  import ChatRoomActor.*

  //From this behavior we can create an Actor that will
  // accept a chat room session,
  // post a message,
  // wait to see it published,
  // and then terminate.
  def apply(): Behavior[SessionEvent] =
    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case SessionGranted(handle) =>
          handle ! PostMessage("Hello World")
          Behaviors.same
        case SessionDenied(reason) =>
          context.log.error("Session denied. Reason: {}", reason)
          Behaviors.unhandled
        case MessagePosted(screenName, message) =>
          context.log.info("message has been posted by '{}' : {}", screenName, message)
          Behaviors.stopped
      }
    }
}
