package phillybus

import scala.concurrent.Await
import scala.concurrent.duration._
import scalaj.http._

import akka.pattern.ask
import akka.util.Timeout
import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import akka.actor.ActorSystem

import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonDSL._

class Request() extends Actor {
	val log = Logging(context.system, this)

	def getRequest(get : GetRequest) = get match {
    case GetRequest(t : String, m : Map[String, String]) =>
      try {
        val request = Http(get.url).header("content-type", "application/json").params(get.params).option(HttpOptions.readTimeout(5000)).asString
        sender ! request
      } catch {
        case ste : java.net.SocketTimeoutException => 
          log.warning(ste.toString)
          sender ! ste
      }
    case GetRequest(t : String, null) =>
      sender ! Http(get.url).header("content-type", "application/json").option(HttpOptions.readTimeout(5000)).asString
    case _ => log.warning("Not a valid request")
    "Not a valid request"
	}

	def postRequest(post : PostRequest) {
		sender ! Http.post(post.url).params(post.params).asString
	}

	def receive = {
		case get : GetRequest => getRequest(get)
		case post : PostRequest => postRequest(post)
		case _ => log.info("Invalid Request type")
	}
}



/**
object RequestApp extends App {
  implicit val timeout = Timeout(10 seconds)
	implicit val formats = DefaultFormats
  val actorSystem = ActorSystem("requestSystem")
  println(getTransitForRoute(20))	

  def getTransitForRoute(routeId : Int) : List[JSONBus] = {
    val myActor = actorSystem.actorOf(Props[Request], name="requestActor")
    val future = myActor ? GetRequest("http://www3.septa.org/hackathon/TransitView", Map("route" -> routeId.toString))
    val result = Await.result(future, timeout.duration).asInstanceOf[String]
    try {
      val json = parse(result)
      json.extract[List[JSONBus]]
    } catch {
      case ste : java.net.SocketTimeoutException => throw ste
      case _ => List[JSONBus]()
    }
  }

}
**/

case class GetRequest(url : String, params : Map[String, String] = null) {}

case class PostRequest(url : String, params : Map[String, String]) 

case class JSONBus(lat : Either[String, Double], lng : String, label : String, VehicleID : String,
			 BlockID : String, Direction : String, destination : String, Offset : String)
case class JSONSepta(bus : List[JSONBus])
case class JSONRoute(route : Map[Int, List[JSONBus]])
case class JSONTransitAll(data : List[JSONRoute])

case class JSONStop(location_id : Int, location_name : 
                    String, location_lat : String, location_lon : String,
                    distance : String) {

  def asJson() : JObject = {
    ("location_id" -> location_id) ~
    ("location_name" -> location_name) ~
    ("location_lat" -> location_lat) ~
    ("location_lon" -> location_lon) ~
    ("distance" -> distance)    
  }
}
