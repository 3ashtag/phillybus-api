package phillybus

import com.github.nscala_time.time.Imports._

class UpdatedAt(val updateRate: Int) {
  var lastUpdated: DateTime = DateTime.lastYear

  def isUpdated(): Boolean = {DateTime.now > lastUpdated.plusSeconds(updateRate)}
  def markUpdated() = {lastUpdated = DateTime.now}

  //update must call markUpdated()
  // def update()
}
