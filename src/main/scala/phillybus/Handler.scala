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
  implicit val timeout = Timeout(20 seconds)
  implicit val formats = DefaultFormats
  val dbAccess = new DBAccess()
  private val estimator = context.actorSelection("/user/estimatorrouter") 


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
        case e: Exception =>
          e.printStackTrace
      }
    
    case ScheduleByStopid(stopId: Int) =>
      val routes = dbAccess.getRoutesByStop(stopId)

      val estimatedRoutes = new ArrayBuffer[Future[Any]]

      routes.foreach { routeId =>
        estimatedRoutes += estimator ? new Estimate(stopId, routeId)
      }

      import scala.concurrent.ExecutionContext.Implicits.global
      implicit def dateTimeOrdering: Ordering[DateTime] = Ordering.fromLessThan(_ isBefore _)
      var allArrivals = new ArrayBuffer[JSONArrival]

      var future = Await.result(Future.sequence(estimatedRoutes), timeout.duration).foreach(allArrivals ++= _.asInstanceOf[List[JSONArrival]])
      println("BACK")
      allArrivals = allArrivals.sortBy(_.time)
      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(allArrivals.map(_.asJson()).toList))))

    case ScheduleByStopAndRoute(stopId: Int, routeId: String) =>

      val future = estimator ? Estimate(stopId, routeId)
      val arrivals = Await.result(future, timeout.duration).asInstanceOf[List[JSONArrival]]
      
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
      val routesJSON: List[JObject] = routes.map{x => new JSONRouteInfo(x).asJson()}
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
    //  println(result)
      val json = parse(result)
      val jsonbuses = json.extract[JSONSepta]

      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(jsonbuses.bus.map(_.asJson()).toList))))
    } catch {
      case ste: java.net.SocketTimeoutException => throw ste
      case me : org.json4s.MappingException => throw me
      case e: Exception =>e.printStackTrace()
    }
    case _ =>
      println("Failure from RequestActor")
  }
}


