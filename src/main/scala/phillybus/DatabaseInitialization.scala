package phillybus

import java.nio.file.{Files, Paths}
import java.util.Date

import org.squeryl.Session
import org.squeryl.SessionFactory
import org.squeryl.adapters.H2Adapter
import org.squeryl.PrimitiveTypeMode._

import com.github.tototoshi.csv._

object DatabaseInitialization {
  val format = new java.text.SimpleDateFormat("yyyyMMdd")

  def initDB = {
    if(!Files.exists(Paths.get("phillybus.h2.db"))){
      Class.forName("org.h2.Driver");
      SessionFactory.concreteFactory = Some (() =>
          Session.create(
          java.sql.DriverManager.getConnection("jdbc:h2:phillybus"),
          new H2Adapter))
      
      transaction {
        Database.create
        Database.printDdl
      }

      addStops()
      addTrips()
      addRoutes()
      addCalendar()
      addCalendarDates()
      addRoutesStops()
      addRouteDirections()
    }
  }

  def addStops() = {
    val reader = CSVReader.open("src/main/resources/dataSources/stops.txt")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.stops.insert(new Stop(row(0).toInt, row(1), row(2).toDouble, row(3).toDouble))
      }
    }
  }

  def addTrips() = {
    val reader = CSVReader.open("src/main/resources/dataSources/trips.txt")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.trips.insert(new Trip(row(0).toInt, row(1).toInt, row(2).toInt, row(5) == "1"))
      }
    }
  }

  def addRoutes() = {
    val reader = CSVReader.open("src/main/resources/dataSources/routes.txt")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.routes.insert(new Route(row(0).toInt, row(1), row(2), row(3).toInt))
      }
    }
  }

  def addCalendar() = {
    val reader = CSVReader.open("src/main/resources/dataSources/calendar.txt")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.calendar.insert(new Calendar(row(0).toInt, format.parse(row(8)), format.parse(row(9)),
                                              row(1) == "1", row(2) == "1", row(3) == "1",
                                              row(4) == "1", row(5) == "1", row(6) == "1", row(7) == "1"))
      }
    }
  }

  def addCalendarDates() = {
    val reader = CSVReader.open("src/main/resources/dataSources/calendar_dates.txt")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.calendarDates.insert(new CalendarDate(row(0).toInt, format.parse(row(1)), row(2) == "1"))
      }
    }
  }

  def addRoutesStops() = {
    val reader = CSVReader.open("src/main/resources/dataSources/routes_stops.txt")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.routesStops.insert(new RoutesStop(row(0), row(1).toInt))
      }
    }
  }

  def addRouteDirections() = {
    val reader = CSVReader.open("src/main/resources/dataSources/directions.csv")
    val iterator = reader.iterator

    //Throw away line with headers
    iterator.next

    transaction {
      while(iterator.hasNext) {
        val row = iterator.next
        Database.routeDirections.insert(new RouteDirection(row(0), row(1)))
      }
    }
  }
}