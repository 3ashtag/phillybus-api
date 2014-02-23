package phillybus

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer

import akka.actor.Actor
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout

import org.mashupbots.socko.events.HttpRequestEvent
import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.github.nscala_time.time.Imports.{DateTime, DateTimeFormat}
import com.github.nscala_time.time.RichDateTimeFormatter

class StopsHandler(request: HttpRequestEvent) extends Actor {
  implicit val timeout = Timeout(4 seconds)
  implicit val formats = DefaultFormats
  val dbAccess = new DBAccess()
  val dtf =  DateTimeFormat.forPattern("MM-dd-yy HH:mm aa");

  def findClosest(stopCoords: LatLongPair, buses: List[JSONBus], direction: String): JSONBus = {
    val possibleBuses = buses.filter(bus => bus.Direction != direction)
    
    var closestBus : JSONBus = null
    var minDistance : Double = 1000
    for(bus <- buses) {
      val distance = Haversine.haversine(stopCoords.latitude, stopCoords.longitude, bus.lat.toDouble, bus.lng.toDouble)
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

    closestBus
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
      var allArrivals = new ArrayBuffer[JSONArrival]

      for(routeId <- routes) {
          val scheduleFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/BusSchedules", 
          Map("req1" -> stopId.toString, "req2" -> routeId.toString, "req6" -> "5"))
          val busFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/TransitView", 
            Map("route" -> routeId.toString))

          var scheduleString = Await.result(scheduleFuture, timeout.duration).asInstanceOf[String]
          val busString = Await.result(busFuture, timeout.duration).asInstanceOf[String]

          println("HI")
          scheduleString = compact(render(parse(scheduleString) \\ routeId.toString)).replace("/", "-")
          println(scheduleString)
          val jsonSchedule = parse(scheduleString).extract[List[JSONSchedule]]
          println(jsonSchedule)
          val jsonBuses = parse(busString).extract[JSONSepta]

          val direction = dbAccess.getRouteDirection(routeId.toString, jsonSchedule(0).Direction.toInt)
          val stopCoords = dbAccess.getCoordsByStop(stopId)

          val buses = jsonBuses.bus

          val nextBus = findClosest(stopCoords, buses, direction)

          var arrivals: ArrayBuffer[JSONArrival] = new ArrayBuffer[JSONArrival]

          val warnings = "N/A"
          jsonSchedule.foreach{ s=> 
            val dateTime = dtf.parseDateTime(s.DateCalender)
            arrivals += new JSONArrival(routeId.toString, dateTime, 0, warnings) 
          }

          arrivals(0).offset = nextBus.Offset.toInt
          allArrivals ++= arrivals
          
      }
      implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
      allArrivals = allArrivals.sortBy(_.time)
      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(allArrivals.map(_.asJson()).toList))))

    case ScheduleByStopAndRoute(stopId: Int, routeId: String) =>
      val scheduleFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/BusSchedules", 
          Map("req1" -> stopId.toString, "req2" -> routeId, "req6" -> "5"))
      val busFuture = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/TransitView", 
        Map("route" -> routeId))

      var scheduleString = Await.result(scheduleFuture, timeout.duration).asInstanceOf[String]
      val busString = Await.result(busFuture, timeout.duration).asInstanceOf[String]

      println("HI")
      scheduleString = compact(render(parse(scheduleString) \\ routeId)).replace("/", "-")
      println(scheduleString)
      val jsonSchedule = parse(scheduleString).extract[List[JSONSchedule]]
      println(jsonSchedule)
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

      arrivals(0).offset = nextBus.Offset.toInt

      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(arrivals.map(_.asJson()).toList))))

    case GetAllRoutes() =>
      val routes = dbAccess.getAllRoutes()
      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(routes.map(new JString(_))))))

    case _ =>
      println("Failure from StopsActor")
  }
}


class RouteHandler(request : HttpRequestEvent) extends Actor {
  implicit val timeout = Timeout(10 seconds)
  implicit val formats = DefaultFormats
  val dbAccess = new DBAccess()

  def receive = {
    case RoutesByStopId(stopId: Int) =>
      val routes = dbAccess.getRoutesByStop(stopId)
      val routesJSON: List[JObject] = routes.map{x => new JSONRouteInfo(x.toString).asJson()}
      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(routesJSON.toList))))
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


