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

class StopsHandler(request: HttpRequestEvent) extends Actor {
  implicit val timeout = Timeout(10 seconds)
  implicit val formats = DefaultFormats
  val dbAccess = new DBAccess()

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
      routes.foreach((routeId: Int) => {
        val future = context.system.actorOf(Props[Request]) ? GetRequest("http://www3.septa.org/hackathon/BusSchedules", Map("req1" ->
          stopId.toString, "req2" -> routeId.toString, "req3" -> "i", "req6" -> "5"))
        val result = Await.result(future, timeout.duration).asInstanceOf[String]
        // println(result)
      })

    case GetAllRoutes() =>
      val routes = dbAccess.getAllRoutes()
      request.response.contentType = "application/json"
      request.response.write(compact(render(new JArray(routes.map(new JInt(_))))))

    case _ =>
      println("Failure from StopsActor")
    
  }
}
