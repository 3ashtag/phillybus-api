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
}