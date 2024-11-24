package server.service

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import database.dao.FileDAO
import helpers.FileHandlerHelper
import org.json.JSONObject


class DBService {
    // Remove all the entries from file_meta that is not present in the file system
    fun removeAbsentEntries(context: Context, fileDAO: FileDAO): Int {
        var removedEntries = 0
        val allFiles = fileDAO.getAllFiles();
        for (fileMeta in allFiles) {
            val file = DocumentFile.fromSingleUri(context, fileMeta.fileUri)
            if (file == null || !file.exists()) {
                // Delete fileMeta from database if DocumentFile does not exist
                fileDAO.deleteFile(fileMeta)
                removedEntries += 1
            }
        }

        return removedEntries;
    }

    fun getScreenshot(fileID:Long,fileDAO: FileDAO){


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