package phillybus

import akka.actor.ActorSystem
import akka.actor.Props

import org.mashupbots.socko.events.HttpResponseStatus
import org.mashupbots.socko.routes._
import org.mashupbots.socko.webserver.{WebServer, WebServerConfig}
import org.slf4j.{Logger, LoggerFactory}

// TEMPORARY
import akka.actor.Actor
import org.mashupbots.socko.events.HttpRequestEvent
// END TEMPORARY

class HelloHandler extends Actor {
  def receive = {
    case event: HttpRequestEvent =>
      event.response.write("HELLO")
      context.stop(self)
  }
}

object Server extends App {
  object LatitudeQueryString extends QueryStringField("lat")
  object LongitudeQueryString extends QueryStringField("long")

  private val log = LoggerFactory.getLogger(getClass)
  val actorSystem = ActorSystem("PhillyBusFinderSystem")
  
  DatabaseInitialization.initDB

  def parseDouble(s: String) = try { Some(s.toDouble)  } catch { case _ => None  }
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
      case _ => {
        request.response.write(HttpResponseStatus.BAD_REQUEST)
      }
    }
  })

  val config = new WebServerConfig(actorSystem.settings.config, "philly-config")
  val webServer = new WebServer(config, routes, actorSystem)
  webServer.start()
  println("Server is Starting")
  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run { webServer.stop()  }
  })
}
