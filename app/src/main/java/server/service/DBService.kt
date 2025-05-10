package server.service

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import database.AppDatabase
import java.io.InputStream
import database.dao.FileDAO
import helpers.FileHandlerHelper
import org.json.JSONObject


class DBService(private val database: AppDatabase) {
    // Remove all the entries from file_meta that is not present in the file system
    fun removeAbsentEntries(filesInStorage:HashSet<String>): Int {
        var removedEntries = 0
        val fileDAO=database.fileDao()
        val allFilesInDB =  fileDAO.getAllFiles();
        for (fileMeta in allFilesInDB) {
            if(!filesInStorage.contains(fileMeta.fileName)){
                fileDAO.deleteFile(fileMeta)
                removedEntries += 1
            }
        }

        return removedEntries;
    }


    fun insertScreenshotData(postBody:String?, fileDAO: FileDAO): Long {
        if(postBody==null) throw IllegalArgumentException("PostBody not found")

        val jsonBody = JSONObject(postBody)
        val fileId = jsonBody.getLong("fileId")
        val imageData = jsonBody.getString("imageData")

        val fileMeta = fileDAO.getFileById(fileId)
        if(fileMeta!=null){
            fileDAO.updateScreenshotData(fileId, imageData)
        }
        else {
            throw IllegalArgumentException("File with ID $fileId not found")
        }
        return fileId;
    }
}