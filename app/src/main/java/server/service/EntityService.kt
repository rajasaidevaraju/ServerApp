package server.service

import database.AppDatabase
import database.entity.Actress
import org.json.JSONArray
import org.json.JSONObject

enum class EntityType{
    Performers,Categories
}

data class ServiceResult(
    val success: Boolean,
    val message: String,
)

class EntityService(private val database: AppDatabase) {

    private fun validateEntityName(entityName: String): Boolean {
        return entityName.isNotEmpty() &&entityName.length < 21 && entityName.matches("^[A-Za-z ]+$".toRegex())
    }

    fun addEntities(entityType: EntityType, postData:String? ): ServiceResult {

        if(postData==null){
            return ServiceResult(success = false, message = "Missing PostBody")
        }
        val jsonBody = JSONObject(postData)
        if (!jsonBody.has("names") || jsonBody.get("names") !is JSONArray) {
            return ServiceResult(success = false, message = "'names' is missing or not an array")
        }
        val namesArray =jsonBody.getJSONArray("names")
        if(namesArray.length()==0){
            return ServiceResult(success = false, message = "No names provided")
        }
        val entityNames = mutableListOf<String>()
        for (i in 0 until namesArray.length()) {
            var name = namesArray.optString(i).trim()
            if(!validateEntityName(name)){
                val message = when (entityType) {
                    EntityType.Performers -> "Invalid performer name"
                    EntityType.Categories -> "Invalid category name"
                }
                return ServiceResult(success = false, message = message)
            }
            name=name.trim()
            entityNames.add(name)
        }

        if(entityType == EntityType.Performers){
            val performers = entityNames.map { name -> Actress(name = name) }
            val result=database.actressDao().insertActresses(performers)
            val successfulInserts = result.count { it > 0 }
            return ServiceResult(success = true,message = "$successfulInserts Performers added successfully")
        }else{
            return ServiceResult(success = false, message = "Implementation pending")
        }
    }

    fun deleteEntities(entityType: EntityType, postData: String?): ServiceResult {
        if (postData == null) {
            return ServiceResult(success = false, message = "Missing Post Body")
        }

        val jsonBody = JSONObject(postData)
        if (!jsonBody.has("ids") || jsonBody.get("ids") !is JSONArray) {
            return ServiceResult(success = false, message = "'ids' is missing or not an array")
        }

        val idsArray = jsonBody.getJSONArray("ids")
        if (idsArray.length() == 0) {
            return ServiceResult(success = false, message = "No IDs provided")
        }

        val entityIds = mutableListOf<Long>()
        for (i in 0 until idsArray.length()) {
            val id = idsArray.optLong(i, -1)
            if (id <= 0) {
                return ServiceResult(success = false, message = "Invalid ID found in the list")
            }
            entityIds.add(id)
        }

        return if (entityType == EntityType.Performers) {
            val deletedCount = database.actressDao().deleteActressesByIds(entityIds)
            val message = if (deletedCount == 1) "Performer deleted successfully" else "$deletedCount Performers deleted successfully"
            ServiceResult(success = true, message = message)
        } else {
            ServiceResult(success = false, message = "Implementation pending")
        }
    }

    fun updateEntity(entityType: EntityType, postData: String?,id:Long?): ServiceResult {

        if (postData == null) {
            return ServiceResult(success = false, message = "Missing PostBody")
        }
        if(id==null){
            return ServiceResult(success = false, message = "Missing ID")
        }
        val jsonBody = JSONObject(postData)
        if (!jsonBody.has("updatedName") || jsonBody.get("updatedName") !is String) {
            return ServiceResult(success = false, message = "'updatedName' is missing or not an string")
        }
        var updatedName=jsonBody.getString("updatedName")
        if(!validateEntityName(updatedName)){
            val message = when (entityType) {
                EntityType.Performers -> "Invalid performer name"
                EntityType.Categories -> "Invalid category name"
            }
            return ServiceResult(success = false, message = message)
        }
        updatedName=updatedName.trim()
        if (entityType == EntityType.Performers) {
            val performer= database.actressDao().getActressById(id)
                ?: return ServiceResult(success = false, message = "Performer with ID $id not found")
            performer.name=updatedName
            val result=database.actressDao().updateActress(performer)
            if(result==0){
                return ServiceResult(success = false, message = "Update of Performer with ID $id unsuccessful")
            }

            return ServiceResult(success = true, message = "Update of Performer with ID $id successful")

        }else{
            return ServiceResult(success = false, message = "Implementation pending")
        }


    }


}