package phillybus

import akka.actor.ActorSystem
import akka.actor.Props

import org.mashupbots.socko.events.HttpResponseStatus
import org.mashupbots.socko.routes._
import org.mashupbots.socko.webserver.{WebServer, WebServerConfig}
import org.slf4j.{Logger, LoggerFactory}


object Server extends App {
  object LatitudeQueryString extends QueryStringField("lat")
  object LongitudeQueryString extends QueryStringField("long")
  object StopIdQueryString extends QueryStringField("stopId")
  object RouteIdQueryString extends QueryStringField("routeId")

  private val log = LoggerFactory.getLogger(getClass)
  val actorSystem = ActorSystem("PhillyBusFinderSystem")
  
  val estimator = actorSystem.actorOf(Props[EstimatorSupervisor]) 
  DatabaseInitialization.initDB

  def parseDouble(s: String) = try { Some(s.toDouble)  } catch { case _ => None  }
  def parseInt(s: String) = try { Some(s.toInt)  } catch { case _ => None  }
  val routes = Routes({
    case HttpRequest(request) => request match {
      case (GET(Path("/stops/nearby")) & LatitudeQueryString(latitude) & LongitudeQueryString(longitude)) => {
        (parseDouble(latitude), parseDouble(longitude)) match {
          case (Some(lat), Some(long)) =>
            actorSystem.actorOf(Props(new StopsHandler(request))) ! LatLongPair(lat, long)
          case _ =>
            request.response.write(HttpResponseStatus.BAD_REQUEST)
         }
      }

      case (GET(Path("/stops/schedules")) & StopIdQueryString(stopId) & RouteIdQueryString(routeId)) => {
        (parseInt(stopId)) match {
          case Some(stop) =>
            actorSystem.actorOf(Props(new StopsHandler(request))) ! ScheduleByStopAndRoute(stop, routeId)
          case _ =>
            request.response.write(HttpResponseStatus.BAD_REQUEST)
        }
      }

      case (GET(Path("/stops/schedules")) & StopIdQueryString(stopId)) => {
        (parseInt(stopId)) match {
          case Some(stop) =>
            actorSystem.actorOf(Props(new StopsHandler(request))) ! ScheduleByStopid(stop)
          case _ =>
            request.response.write(HttpResponseStatus.BAD_REQUEST)
        }
      }
      
      case (GET(Path("/routes"))) => {
        actorSystem.actorOf(Props(new StopsHandler(request))) ! GetAllRoutes()
      }

      case (GET(Path("/routes")) & StopIdQueryString(stopId)) => {
        (parseInt(stopId)) match {
          case Some(stop) =>
            actorSystem.actorOf(Props(new RouteHandler(request))) ! RoutesByStopId(stop)
          case _ =>
            request.response.write(HttpResponseStatus.BAD_REQUEST)
        }
      }

      case (GET(PathSegments("routes" :: routeId :: Nil))) => {        
          actorSystem.actorOf(Props(new BusByRouteHandler(request))) ! RouteId(routeId)
      }
      case _ => {
        println(request.endPoint)
        request.response.write(HttpResponseStatus.BAD_REQUEST)
      }
    }
  })

  val config = new WebServerConfig(port=try { sys.env("PORT").toInt  } catch {case e: Exception => 8686})
  val webServer = new WebServer(config, routes, actorSystem)
  webServer.start()
  println("Server is Starting")
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run { webServer.stop()  }
  })
}
