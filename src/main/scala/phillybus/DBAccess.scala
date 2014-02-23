package phillybus

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.H2Adapter
import org.squeryl.PrimitiveTypeMode._

class DBAccess {

  def getRoutesByStop(stopId: Int): List[Int] = {
    Class.forName("org.h2.Driver");
      SessionFactory.concreteFactory = Some (() =>
          Session.create(
          java.sql.DriverManager.getConnection("jdbc:h2:phillybus"),
          new H2Adapter))

    transaction {
      val routes = from(Database.routesStops)(row=>
        where(row.stop_id === stopId)
        select(row.route_id)
      )
      routes.toList
    }
  }

  def getAllRoutes(): List[Int] = {
    Class.forName("org.h2.Driver");
      SessionFactory.concreteFactory = Some (() =>
          Session.create(
          java.sql.DriverManager.getConnection("jdbc:h2:phillybus"),
          new H2Adapter))
    
    transaction {
      val routes = from(Database.routes)(row=>
        where(1 === 1)
        select(row.id)
      )

      routes.toList
    }
  }

  def getRouteDirection(routeName: String, direction: Int): String = {
    Class.forName("org.h2.Driver");
      SessionFactory.concreteFactory = Some (() =>
          Session.create(
          java.sql.DriverManager.getConnection("jdbc:h2:phillybus"),
          new H2Adapter))

    transaction {
      val zeroMeans = Database.routeDirections.where(row => row.route_name === routeName).single.zero_dir
      if(direction == 0)
        zeroMeans
      else {
        zeroMeans match {
          case "Southbound" =>
            "Northbound"
          case "Northbound" =>
            "Southbound"
          case "Eastbound" =>
            "Westbound"
          case "Westbound" =>
            "Eastbound"
        }
      }
    }
  }

  def getCoordsByStop(stopId: Int): LatLongPair = {
    Class.forName("org.h2.Driver");
      SessionFactory.concreteFactory = Some (() =>
          Session.create(
          java.sql.DriverManager.getConnection("jdbc:h2:phillybus"),
          new H2Adapter))

    transaction {
      val stopData = Database.stops.where(row => row.id === stopId).single
      new LatLongPair(stopData.stop_lon, stopData.stop_lat)
    }
  }
}