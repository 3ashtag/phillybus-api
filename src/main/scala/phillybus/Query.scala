package phillybus

case class LatLongPair(longitude: Double, latitude: Double)
case class BusLocationById(busId: Int)
case class ScheduleByStopid(stopId: Int)
case class ScheduleByStopAndRoute(stopId: Int, routeId: String) 
case class GetAllRoutes()
case class RouteId(routeId : String)
