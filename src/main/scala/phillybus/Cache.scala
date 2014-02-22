package phillybus

import akka.actor.{Actor}

object Cache extends Actor {
  var stopLocations: StopLocations = new StopLocations()
  var busLocations: BusLocations =  new BusLocations(30)

  def receive = {
    //Doesn't need caching since it doesn't change
    case StopsByLocation(longitude: Double, latitude: Double) =>
      println("Need stops at: " + longitude + ", " + latitude)
    case BusLocationById(busId: Int) =>
      busLocations.ensureIsUpdated()
      sender ! busLocations.getId(busId)
    case _ =>
      println("Error: in ")
  }
}
