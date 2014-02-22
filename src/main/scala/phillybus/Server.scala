package phillybus

import akka.actor.ActorSystem
import akka.actor.Props

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
  private val log = LoggerFactory.getLogger(getClass)
  val actorSystem = ActorSystem("PhillyBusFinderSystem")
  val routes = Routes({
    case GET(request) => {
      actorSystem.actorOf(Props[HelloHandler]) ! request
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
