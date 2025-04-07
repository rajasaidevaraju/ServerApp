# MKHttpServer Android App

## Overview

MKHttpServer is an Android application that runs a local HTTP server directly on your device. It allows you to manage, browse, and stream media files (primarily videos) stored in selected folders on your device's internal storage or SD card via a web interface or API calls. The server runs as a foreground service for reliability and provides features for user authentication, performer management, and basic server discovery.

## Key Features

* **Embedded HTTP Server:** Runs a lightweight NanoHTTPD server on a configurable port (default 1280).
* **File Management:**
    * Scans user-selected folders (Internal Storage / SD Card) for media files.
    * Stores file metadata (name, URI, optional thumbnail) in a local Room database.
    * API endpoints for listing files with pagination.
    * File streaming with support for HTTP Range requests (partial content).
    * File uploading via multipart/form-data.
    * File renaming and deletion (updates both filesystem and database).
    * Thumbnail generation/storage (Base64 encoded).
    * Database cleanup for entries pointing to non-existent files.
    * File path repair mechanism.
* **Performer Management:**
    * Create, Read, Update, Delete (CRUD) operations for performers.
    * Associate performers with media files (many-to-many relationship).
    * API endpoint to list performers with the count of associated files.
* **User Authentication:**
    * User management (create/delete/disable/enable users, change passwords) via a dedicated screen in the app.
    * Secure login endpoint using username and password (hashed using jBCrypt).
    * Session management using Bearer tokens (UUID-based).
    * Most API endpoints require authentication.
* **Server Discovery:** Uses UDP broadcasts to discover other instances of the server on the local network.
* **Device Statistics:** API endpoint (`/stats`) to retrieve device storage information and battery status.
* **UI Serving Options:**
    * Serves a static, built-in web UI from the app's assets.
    * Alternatively, can proxy UI requests to a separate Next.js (or other) development server running elsewhere (configurable).
* **Background Operation:** Runs as an Android Foreground Service for stability, showing a persistent notification.
* **Modern Android Development:** Built with Kotlin, Jetpack Compose, Room, Coroutines, ViewModel, and LiveData/StateFlow.

## Technology Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose
* **Database:** Room Persistence Library
* **HTTP Server:** NanoHTTPD
* **Asynchronous Programming:** Kotlin Coroutines
* **Architecture:** MVVM (ViewModel, LiveData/StateFlow)
* **JSON Processing:** Gson
* **Password Hashing:** jBCrypt
* **Networking:** Android Networking APIs, UDP Sockets

## Setup and Configuration

1.  **Build & Install:** Build the Android project and install the APK on your device.
2.  **Permissions:** The app will implicitly request storage access when you select folders. Ensure you grant necessary permissions.
3.  **Select Folders:**
    * Open the app's main screen.
    * You **must** select an **Internal Storage Folder**. This folder is used for uploads and is essential for features like file repair.
    * Optionally, select an **SD Card Folder** if you want the server to manage files from your external storage.
4.  **Configure UI Serving (Optional):**
    * Decide whether to use the built-in static UI or proxy to a development server using the toggle switch.
    * If using the proxy mode, tap "Edit Address" for the "Front End Server" and enter the IP address and port of your running development UI server (e.g., `192.168.1.101:3000`).
5.  **Manage Users:**
    * Navigate to the "Manage Users" screen from the main UI.
    * Create at least one user account if you intend to use authenticated API endpoints. Remember the username and password.
6.  **Start Server:**
    * Tap the "Start Server" button on the main screen for the "Back End Server".
    * The app will start the foreground service and display the server's IP address and port (e.g., `192.168.1.102:1280`).

## API Usage

The application exposes an HTTP API for interacting with its features.

* **Base Path:** All API endpoints described in the OpenAPI specification are relative to the `/server` path. For example, the status endpoint is accessed at `http://<server-ip>:1280/server/status`.
* **Default Port:** `1280`
* **Authentication:**
    * Most endpoints require authentication.
    * First, obtain a Bearer token by calling the `POST /server/login` endpoint with valid user credentials.
    * Include this token in the `Authorization` header for subsequent requests: `Authorization: Bearer <your-token>`.
    * Endpoints like `/login` and `/status` do not require authentication.
* **API Documentation:** A detailed OpenAPI 3.0 specification is available (`openapi.json` or similar, if generated) which describes all available endpoints, parameters, request bodies, and responses.
* **`postData` Field:** Note that several `POST` and `PUT` endpoints expect data in `application/x-www-form-urlencoded` format, specifically with a single field named `postData` which contains a *JSON string* as its value. Refer to the OpenAPI spec for details on the expected JSON structure within this field for each endpoint. File uploads (`POST /server/file`) use standard `multipart/form-data`.

## Project Structure (Key Packages)

* `com.example.serverapp`: Contains Activities, Service, and UI-related Composables.
* `com.example.serverapp.viewmodel`: ViewModels for managing UI state and business logic.
* `database`: Room database definitions (Entities, DAOs, Database class, Migrations).
    * `dao`: Data Access Objects for interacting with the database.
    * `entity`: Room entity classes representing database tables.
    * `jointable`: Entities for many-to-many relationship cross-reference tables.
* `helpers`: Utility classes for permissions, file handling, network operations, and shared preferences.
* `server`: Core server logic.
    * `controller`: Handles specific groups of API requests (Files, Performers).
    * `service`: Contains business logic services used by controllers and the server (Database operations, File system interactions, Authentication, Session Management, Network processing).
    * `MKHttpServer.kt`: Main NanoHTTPD server implementation, routing, and middleware.

## License
MIT License
.
