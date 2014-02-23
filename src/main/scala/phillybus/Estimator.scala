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

  
  def findClosest(stopCoords: LatLongPair, buses: List[JSONBus]): JSONBus = {
    
    var closestBus : JSONBus = null
    var minDistance : Double = 1000
    for(bus <- buses) {
      val distance = Haversine.haversine(stopCoords.latitude, stopCoords.longitude, bus.lat.toDouble, bus.lng.toDouble)
      val pass = bus.Direction.replace("B", "b") match {
        case "Southbound" => 
          bus.lat.toDouble >= stopCoords.latitude
        case "Northbound" => 
          bus.lat.toDouble <= stopCoords.latitude
        case "Eastbound" => 
          bus.lng.toDouble <= stopCoords.longitude
        case "Westbound" => 
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
  
      var otherDestination = "" 
      if(jsonBuses.bus.length > 0)
        otherDestination = jsonBuses.bus(0).destination

      val buses = jsonBuses.bus.filter(bus => bus.Direction.replace("B", "b") == direction.replace("B", "b"))
      var destination = ""
      if(buses.length > 0)
        destination = buses(0).destination
      else
        destination = otherDestination

      val nextBus = findClosest(stopCoords, buses)
  
      var arrivals: ArrayBuffer[JSONArrival] = new ArrayBuffer[JSONArrival]
  
      val warnings = "N/A"
  
      jsonSchedule.foreach{ s => 
        var offset = 0
        if(nextBus != null) {
           offset = nextBus.Offset.toInt
        }

        val dateTime = dtf.parseDateTime(s.DateCalender)
        val cutOffTime = DateTime.now.minusMinutes(offset)
        if(cutOffTime.isBefore(dateTime)) {
          arrivals += new JSONArrival(routeId.toString, destination, dateTime, 0, warnings) 
        }
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
