package phillybus

import scalaj.http._
import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import akka.actor.ActorSystem
import scala.collection.mutable.HashMap

class Request() extends Actor {
	val log = Logging(context.system, this)

	def getRequest(get : GetRequest): String = {
		val request = Http(get.url).asString
	}

	def postRequest(postRequest : PostRequest) : String = {
		"test"

	}

	def receive = {
		case get : GetRequest => getRequest(get)
		case post : PostRequest => postRequest(post)
		case _ => log.info("Invalid Request type")
	}
}

object RequestApp extends App {
	val actorSystem = ActorSystem("requestSystem")
	val myActor = actorSystem.actorOf(Props[Request], name="requestActor")
}

case class GetRequest(url : String, header : String, params : HashMap[String, String]) {}

case class PostRequest(url : String, params : HashMap[String, String]) 