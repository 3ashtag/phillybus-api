package phillybus

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout

import org.mashupbots.socko.events.HttpRequestEvent
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.github.nscala_time.time.Imports._

class StopsHandler(request: HttpRequestEvent) extends Actor {
  implicit val timeout = Timeout(10 seconds)
  implicit val formats = DefaultFormats
  val dbAccess = new DBAccess()

  def findClosest(stopCoords: LatLongPair, buses: List[JSONBus], direction: String): JSONBus = {
    val possibleBuses = buses.filter(bus => bus.direction != direction)
    
    var closestBus = null
    var minDistance = 10001000100010000
    for(bus <- buses) {
      val distance = Haversine.haversine(stop.latitude, stop.longitude, bus.latitude, bus.longitude)
      if(closestBus != null){
        if(distance < minDistance){
          closestBus = bus
          minDistance = distance
        }
      } else {
        closestBus = bus
        minDistance = distance
      }
    }

    return closestBus
  }

  def receive = {
    case LatLongPair(latitude: Double, longitude: Double) =>
      val future = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/locations/get_locations.php", Map("lon" ->
        longitude.toString, "lat" -> latitude.toString, "radius" -> (.5).toString, "type" -> "bus_stops"))
      val result = Await.result(future, timeout.duration).asInstanceOf[String]

      try {
        val json = parse(result)
        val jsonstop = json.extract[List[JSONStop]]

        request.response.contentType = "application/json"
        request.response.write(compact(render(new JArray(jsonstop.map(_.asJson()).toList))))
      } catch {
        case ste: java.net.SocketTimeoutException => throw ste
        case _ => List[JSONStop]()
      }
    
    case ScheduleByStopid(stopId: Int) =>
      val routes = dbAccess.getRoutesByStop(stopId)
      val routesProcessed = new ArrayBuffer[Future[Any]]

      routes.foreach((routeId: Int) => {
        println("HI")
      }

    case ScheduleByStopAndRoute(stopId: Int, routeId: String) 
      val scheduleFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/BusSchedules", 
          Map("req1" -> stopId.toString, "req2" -> routeId, "req6" -> "5"))
      val busFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/TransitView", 
        Map("route" -> routeId))

      val scheduleString = Await.result(scheduleFuture, timeout.duration).asInstanceOf[String]
      val busString = Await.result(busFuture, timeout.duration).asInstanceOf[String]

      val jsonSchedule = json.extract[JSONSepta]
      val jsonBus = parse(busString).extract[JSONSepta]

      val direction = dbAccess.getRouteDirection(routeId, jsonSchedule.Direction)
      val stopCoords = dbAccess.getCoordsByStop(stopId)
      val nextBus = findClosest(buses)

      request.response.contentType = "application/json"
      if(nextBus != null) {
        request.response.write(compact(render(nextBus)))
      } else {
        request.response.write(compact(render(new JArray())))
      }

    case GetAllRoutes() =>
      val routes = dbAccess.getAllRoutes()
      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(routes.map(new JInt(_))))))

    case _ =>
      println("Failure from StopsActor")
  }
}

class BusByRouteHandler(request : HttpRequestEvent) extends Actor {
  implicit val timeout = Timeout(10 seconds)
  implicit val formats = DefaultFormats

  def receive = {
    case RouteId(routeId: String) =>
    val future = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/TransitView", 
      Map("route" -> routeId))
    val result = Await.result(future, timeout.duration).asInstanceOf[String]
    try {
      println(result)
      val json = parse(result)
      val jsonbuses = json.extract[JSONSepta]

      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(jsonbuses.bus.map(_.asJson()).toList))))
    } catch {
      case ste: java.net.SocketTimeoutException => throw ste
      case me : org.json4s.MappingException => throw me
      case _ => List[JSONSepta]()
    }
    case _ =>
      println("Failure from RequestActor")
  }
}


