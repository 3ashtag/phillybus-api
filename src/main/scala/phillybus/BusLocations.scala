package phillybus

class BusLocations(updateRate: Int) {
  var updatedAt = new UpdatedAt(updateRate)

  def ensureIsUpdated() = {
    if(updatedAt.isUpdated()) {
      update()
    }
  }

	def update() = {updatedAt.markUpdated()}

  def getId(id: Int): Int = {
    id
  }
}

class StopLocations() {}