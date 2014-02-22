package phillybus

import org.squeryl.{Schema, KeyedEntity}
import org.squeryl.PrimitiveTypeMode._
import org.squeryl.annotations.Column

import java.util.Date
import java.sql.Timestamp

object Database extends Schema {
  val stops = table[Stop]
  val stopTimes = table[StopTime]
  val trips = table[Trip]
  val routes = table[Route]
  val calendar = table[Calendar]
  val calendarDates = table[CalendarDate]
  val routesStops = table[RoutesStop]

}

case class Stop(
    val id: Int,
    val stop_name: String,
    val stop_lat: Double,
    val stop_lon: Double)

case class StopTime(
    val trip_id: Int,
    val stop_id: Int,
    val arrival_time: String,
    val departure_time: String)

case class Trip(
    val route_id: Int,
    val service_id: Int,
    val trip_id: Int,
    val direction_id: Boolean)

case class Route(
    val id: Int,
    val route_short_name: String,
    val route_long_name: String,
    val route_type: Int)

case class Calendar(
    val service_id: Int,
    val start_date: Date,
    val end_date: Date,
    val monday: Boolean,
    val tuesday: Boolean,
    val wednesday: Boolean,
    val thursday: Boolean,
    val friday: Boolean,
    val saturday: Boolean,
    val sunday: Boolean)

case class CalendarDate(
    val service_id: Int,
    val date: Date,
    //True=service added, false=service removed
    val exception_type: Boolean)

case class RoutesStop(
  val route_id: Int,
  val stop_id: Int)