package chat_room

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ChatRoomActor {

  //Using a sealed trait and case class/objects
  // to represent multiple messages an actor can receive
  sealed trait RoomMsg
  final case class GetSession(screenName: String, replyTo: ActorRef[SessionEvent])
    extends RoomMsg

  // We do not want to give the ability
  // to send PublishSessionMessage commands to arbitrary clients,
  // so we reserve that right to the internal session actors we
  // create — otherwise clients could pose as
  // completely different screen names
  // (imagine the GetSession protocol to include authentication
  // information to further secure this).
  // Therefore PublishSessionMessage has private visibility
  // and can’t be created outside the ChatRoom object.
  private final case class PublishSessionMessage(screenName: String, message: String)
    extends RoomMsg

  sealed trait SessionEvent
  final case class SessionGranted(handle: ActorRef[PostMessage]) extends SessionEvent
  final case class SessionDenied(reason: String) extends SessionEvent
  final case class MessagePosted(screenName: String, message: String) extends SessionEvent

  sealed trait SessionMsg
  final case class PostMessage(message: String) extends SessionMsg
  private final case class NotifyClient(messagePosted: MessagePosted) extends SessionMsg

  //==================================================
  def apply(): Behavior[RoomMsg] =
    chatRoom(List.empty)

  private def chatRoom(sessions: List[ActorRef[SessionMsg]]): Behavior[RoomMsg] =
    Behaviors receive { (context, message) =>
      message match
        case GetSession(screenName, client) =>
          // create a child actor for further interaction with the client
          val sessionActor =
            context.spawn(
              session(screenName, context.self, client),
              name = URLEncoder.encode(screenName, StandardCharsets.UTF_8.name)
            )
          client ! SessionGranted(sessionActor)
          chatRoom(sessionActor :: sessions)
        case PublishSessionMessage(screenName, message) =>
          val notificationMsg =
            NotifyClient(MessagePosted(screenName, message))
          sessions.foreach(_ ! notificationMsg)
          Behaviors.same
    }

  private def session(
                     screenName: String,
                     room: ActorRef[PublishSessionMessage],
                     client: ActorRef[SessionEvent]
                     ): Behavior[SessionMsg] =
    Behaviors.receiveMessage {
      case PostMessage(message) =>
        // from client, publish it to others via the room
        room ! PublishSessionMessage(screenName, message)
        Behaviors.same
      case NotifyClient(messagePosted) =>
        //published from the room
        client ! messagePosted
        Behaviors.same
    }
}