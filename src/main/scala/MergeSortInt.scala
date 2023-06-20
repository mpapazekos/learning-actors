import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.annotation.tailrec
import scala.util.Random

object MergeSortInt {
  
  def main(args: Array[String]): Unit =
    val list = (for (_ <- 1 to 100) yield Random.between(1, 101)).toList
    ActorSystem(sort(list), "MySystem")
  
  final case class Sorted(result: List[Int])

  private def mergesort(list: List[Int], replyTo: ActorRef[Sorted]): Behavior[Sorted] =
    Behaviors
      .setup { (context: ActorContext[Sorted]) =>
        if list.size <= 1 then
          replyTo ! Sorted(list)
          Behaviors.stopped
        else
          val mid = list.size / 2
          val (left, right) = list.splitAt(mid)

          context.spawnAnonymous(mergesort(left, replyTo = context.self))
          context.spawnAnonymous(mergesort(right, replyTo = context.self))

          waitingForMerge(None, replyTo)
      }

  private def waitingForMerge(firstToArrive: Option[List[Int]], replyTo: ActorRef[Sorted]): Behavior[Sorted] =
    Behaviors.receiveMessage {
      case Sorted(list) =>
        if firstToArrive.isEmpty then
          waitingForMerge(Some(list), replyTo)
        else
          // Second list arrived
          // merge first and received list
          val merged: List[Int] = merge(firstToArrive.get, list)
          replyTo ! Sorted(merged)
          Behaviors.stopped
    }

  private def merge(left: List[Int], right: List[Int]): List[Int] =

    @tailrec
    def loop(result: List[Int], left: List[Int], right: List[Int]): (List[Int], List[Int], List[Int]) =
      if left.isEmpty || right.isEmpty then
        (result, left, right)
      else if left.head < right.head then
        loop(result :+ left.head, left.tail, right)
      else
        loop(result :+ right.head, left, right.tail)

    val (result, leftRes, rightRes) = loop(List.empty[Int], left, right)

    if leftRes.isEmpty then
      result :++ rightRes
    else
      result :++ leftRes

  private def sort(list: List[Int]): Behavior[Sorted] =
    Behaviors.setup { ctx =>
      ctx.log.info(s"Before: ${list.toString}")
      ctx.spawnAnonymous(mergesort(list, ctx.self))
      Behaviors.receiveMessage {
        case Sorted(result) =>
          ctx.log.info("After: {}", result.toString)
          Behaviors.stopped
      }
    }
}