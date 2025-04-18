package helpers

import android.content.Context
import android.content.Context.STORAGE_SERVICE
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import database.entity.FileMeta
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse


class FileHandlerHelper(private val context: Context){

    private val invalidChars = Regex("[<>:\"/\\\\|?*\\x00-\\x1F]")

    fun isSdCardAvailable():Boolean{
        var sdCardAvailable = false
        val  storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager;
        val storageVolumes = storageManager.storageVolumes;
        for (volume in storageVolumes) {
            val description = volume.getDescription(context)
            if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED && description.contains("SD")) {
                sdCardAvailable = true
                break
            }
        }

        return sdCardAvailable
    }

    fun getInternalVolume(): StorageVolume? {
        val storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager;
        val storageVolume = storageManager.primaryStorageVolume;
        return storageVolume
    }

    fun getExternalVolume(): StorageVolume?{
        val  storageManager = context.getSystemService(STORAGE_SERVICE) as StorageManager;
        val storageVolumes = storageManager.storageVolumes;
        for (volume in storageVolumes) {
            val description = volume.getDescription(context)
            if (volume.isRemovable && volume.state == Environment.MEDIA_MOUNTED && description.contains("SD")) {
                return volume
            }
        }
        return null
    }

    fun getFolderNameFromUri(uri: Uri?): String? {

        val segments = uri?.pathSegments

        return if(segments.isNullOrEmpty()){
            null
        }else{
            segments.last()
        }

    }


    fun isValidFilename(name: String?): Boolean {
        if (name.isNullOrBlank()) {
            return false
        }

        if (name.length > 240) {
            return false
        }

        if (invalidChars.containsMatchIn(name)) {
            return false
        }
        return true
    }

    fun getUriForFileName(uri: Uri, fileName: String): DocumentFile? {
        val documentTree = DocumentFile.fromTreeUri(context, uri)

        if (documentTree != null && documentTree.isDirectory) {
            val fileList = mutableListOf<Uri>()

            traverseDirectory(documentTree, fileName, fileList)

            // Find the file with the given filename in the list of URIs
            val fileUri=fileList.firstOrNull()
            if(fileUri!=null){
                return DocumentFile.fromSingleUri(context, fileUri)
            }

        }

        return null
    }

    private fun traverseDirectory(directory: DocumentFile, fileName: String, fileList: MutableList<Uri>) {
        val files = directory.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                traverseDirectory(file, fileName, fileList)
            } else {
                if (file.name == fileName) {
                    fileList.add(file.uri)
                }
            }
        }
    }

    fun getAllFilesMetaInDirectory(uri: Uri): List<FileMeta> {
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        val fileMetas = mutableListOf<FileMeta>()

        documentFile?.let { dir ->
            for (file in dir.listFiles()) {
                if (file.isDirectory) {
                    fileMetas.addAll(getAllFilesMetaInDirectory(file.uri))
                } else {
                    val fileName=file.name
                    if(fileName!=null){
                        fileMetas.add(FileMeta(fileName=fileName, fileUri = file.uri))
                    }
                }
            }
        }

        return fileMetas
    }

    fun serveStaticFile(filePath: String): NanoHTTPD.Response? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open("web/$filePath")
            val fileBytes = inputStream.readBytes()
            val mimeType = getMimeType(filePath)
            newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, fileBytes.inputStream(), fileBytes.size.toLong())
        } catch (e: Exception) {
            Log.e("MKHttpServer", "Error serving file: $filePath", e)
            null
        }
    }

    // Helper to determine MIME type based on file extension
    private fun getMimeType(filePath: String): String {
        return when {
            filePath.endsWith(".html") -> "text/html"
            filePath.endsWith(".svg") -> "image/svg+xml"
            filePath.endsWith(".css") -> "text/css"
            filePath.endsWith(".js") -> "application/javascript"
            filePath.endsWith(".png") -> "image/png"
            filePath.endsWith(".jpg") || filePath.endsWith(".jpeg") -> "image/jpeg"
            filePath.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }




}