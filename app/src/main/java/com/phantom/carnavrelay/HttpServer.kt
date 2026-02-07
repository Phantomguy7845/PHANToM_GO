package com.phantom.carnavrelay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.net.NetworkInterface
import java.util.Collections

class HttpServer(private val context: Context, private val prefsManager: PrefsManager) : NanoHTTPD(prefsManager.getServerPort()) {
    
    companion object {
        private const val TAG = "PHANTOM_GO"
        const val DEFAULT_PORT = 8765
    }
    
    private var isRunning = false
    
    override fun start() {
        try {
            super.start()
            isRunning = true
            Log.d(TAG, "✅ HTTP Server started on port $listeningPort")
        } catch (e: IOException) {
            Log.e(TAG, "💥 Failed to start HTTP Server", e)
            throw e
        }
    }
    
    override fun stop() {
        super.stop()
        isRunning = false
        Log.d(TAG, "🛑 HTTP Server stopped")
    }
    
    fun isServerRunning(): Boolean {
        return isRunning
    }
    
    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri
        
        Log.d(TAG, "📡 HTTP ${method.name} $uri from ${session.remoteIpAddress}")
        
        return when {
            uri == "/status" && method == Method.GET -> handleStatus()
            uri == "/open-url" && method == Method.POST -> handleOpenUrl(session)
            uri == "/refresh-token" && method == Method.POST -> handleRefreshToken(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
    
    private fun handleStatus(): Response {
        val ip = getLocalIpAddress() ?: "0.0.0.0"
        val port = listeningPort
        val token = prefsManager.getServerToken()
        val tokenHint = prefsManager.getTokenHint(token)
        
        val response = JsonUtils.createStatusResponse(ip, port, true, tokenHint)
        return newFixedLengthResponse(Response.Status.OK, "application/json", response)
    }
    
    private fun handleOpenUrl(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        
        val postData = body["postData"] ?: ""
        Log.d(TAG, "📨 Received open-url request: $postData")
        
        val request = JsonUtils.parseOpenUrlRequest(postData)
        
        if (request == null) {
            Log.w(TAG, "❌ Invalid open-url request format")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST, 
                "application/json", 
                JsonUtils.createErrorResponse("Invalid request format")
            )
        }
        
        val (token, url) = request
        val serverToken = prefsManager.getServerToken()
        
        // Verify token
        if (token != serverToken) {
            Log.w(TAG, "❌ Invalid token received")
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                JsonUtils.createErrorResponse("Invalid token")
            )
        }
        
        // Token valid - open Maps
        Log.d(TAG, "✅ Token valid, opening URL: $url")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
            
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                JsonUtils.createOpenUrlResponse(true, "URL opened successfully")
            )
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to open Maps", e)
            
            // Fallback: open with any browser
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    JsonUtils.createOpenUrlResponse(true, "URL opened (fallback)")
                )
            } catch (e2: Exception) {
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    JsonUtils.createErrorResponse("Failed to open URL: ${e2.message}")
                )
            }
        }
    }
    
    private fun handleRefreshToken(session: IHTTPSession): Response {
        // Only allow refresh from localhost
        val remoteIp = session.remoteIpAddress
        if (remoteIp != "127.0.0.1" && remoteIp != "localhost" && !remoteIp.startsWith("192.168.")) {
            Log.w(TAG, "❌ Refresh token attempted from external IP: $remoteIp")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                JsonUtils.createErrorResponse("Token refresh only allowed from local network")
            )
        }
        
        val newToken = prefsManager.refreshServerToken()
        Log.d(TAG, "🔄 Token refreshed, new hint: ${prefsManager.getTokenHint(newToken)}")
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JsonUtils.createRefreshTokenResponse(newToken)
        )
    }
    
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error getting IP address", e)
        }
        return null
    }
    
    fun getPairingPayload(): String {
        val ip = getLocalIpAddress() ?: "0.0.0.0"
        val port = listeningPort
        val token = prefsManager.getServerToken()
        return JsonUtils.createPairingPayload(ip, port, token)
    }
}
