package phillybus

/** Akka Imports **/
import akka.actor.{Actor, ActorRef, Props, OneForOneStrategy}
import akka.actor.SupervisorStrategy.Escalate
import akka.routing.FromConfig

/** External Imports **/
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer

import akka.pattern.ask
import akka.util.Timeout

import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.github.nscala_time.time.Imports.{DateTime, DateTimeFormat}
import com.github.nscala_time.time.RichDateTimeFormatter

case class Estimate(stopId: Int, routeId: String)

class Estimator extends Actor {
  implicit val timeout = Timeout(10 seconds)
  implicit val formats = DefaultFormats

  val dbAccess = new DBAccess()

  val dtf =  DateTimeFormat.forPattern("MM-dd-yy HH:mm aa")

  
  def findClosest(stopCoords: LatLongPair, buses: List[JSONBus], direction: String): JSONBus = {
    val possibleBuses = buses.filter(bus => bus.Direction == direction)
    
    var closestBus : JSONBus = null
    var minDistance : Double = 1000
    for(bus <- buses) {
      val distance = Haversine.haversine(stopCoords.latitude, stopCoords.longitude, bus.lat.toDouble, bus.lng.toDouble)
      val pass = bus.Direction match {
        case "SouthBound" => 
          bus.lat.toDouble >= stopCoords.latitude
        case "NorthBound" => 
          bus.lat.toDouble <= stopCoords.latitude
        case "EastBound" => 
          bus.lng.toDouble <= stopCoords.longitude
        case "WestBound" => 
          bus.lng.toDouble >= stopCoords.longitude
        case _ =>
          true

     }
      if(closestBus != null){
        if(distance < minDistance && pass){
          closestBus = bus
          minDistance = distance
        }
      } else {
        closestBus = bus
        minDistance = distance
      }
    }

    closestBus
  }


  def receive = {
    case Estimate(stopId: Int, routeId: String) =>
      val scheduleFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/BusSchedules", 
          Map("req1" -> stopId.toString, "req2" -> routeId, "req6" -> "5"))
      val busFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/TransitView", 
          Map("route" -> routeId))

      var scheduleString = Await.result(scheduleFuture, timeout.duration).asInstanceOf[String]
      val busString = Await.result(busFuture, timeout.duration).asInstanceOf[String]
  
      scheduleString = compact(render(parse(scheduleString) \\ routeId)).replace("/", "-")
      val jsonSchedule = parse(scheduleString).extract[List[JSONSchedule]]
      val jsonBuses = parse(busString).extract[JSONSepta]
  
      val direction = dbAccess.getRouteDirection(routeId, jsonSchedule(0).Direction.toInt)
      val stopCoords = dbAccess.getCoordsByStop(stopId)
  
      val buses = jsonBuses.bus
  
      val nextBus = findClosest(stopCoords, buses, direction)
  
      var arrivals: ArrayBuffer[JSONArrival] = new ArrayBuffer[JSONArrival]
  
      val warnings = "N/A"
  
      jsonSchedule.foreach{ s=> 
        val dateTime = dtf.parseDateTime(s.DateCalender)
        arrivals += new JSONArrival(routeId, dateTime, 0, warnings)
      }
  
      if(nextBus != null) {
        arrivals(0).offset = nextBus.Offset.toInt
      }
  
      sender ! arrivals.toList
  
    case _ =>
        println("Error in estimator")
  }

}

class EstimatorSupervisor extends Actor {

  // Escalate exceptions, try up to 10 times, if one actor fails, try just that one again
  val escalator = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 seconds) {
    case _: Exception => Escalate
  }

  val router = context.system.actorOf(Props[Estimator].withRouter(
      FromConfig.withSupervisorStrategy(escalator)), 
    name="estimatorrouter")

  def receive = {
    case message: Estimate =>
      router forward message 
    case _ =>
      println("Error: in interpreter supervisor")
  }

}
