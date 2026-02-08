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
        const val ACTION_PAIRING_CHANGED = "com.phantom.carnavrelay.PAIRING_CHANGED"
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
            uri == "/pair" && method == Method.POST -> handlePair(session)
            uri == "/open-url" && method == Method.POST -> handleOpenUrl(session)
            uri == "/refresh-token" && method == Method.POST -> handleRefreshToken(session)
            uri == "/unpair" && method == Method.POST -> handleUnpair(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
    
    private fun handleStatus(): Response {
        val ip = getLocalIpAddress() ?: "0.0.0.0"
        val port = listeningPort
        val token = prefsManager.getServerToken()
        val tokenHint = prefsManager.getTokenHint(token)
        val paired = prefsManager.isDisplayPaired()
        val pairedMainId = prefsManager.getDisplayPairedMainId()
        
        Log.d(TAG, "📊 /status request - paired=$paired, tokenHint=$tokenHint")
        
        val response = JsonUtils.createStatusResponse(ip, port, paired, tokenHint, "1.0", pairedMainId)
        return newFixedLengthResponse(Response.Status.OK, "application/json", response)
    }
    
    private fun handlePair(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        
        val postData = body["postData"] ?: ""
        Log.d(TAG, "📨 /pair request received: $postData")
        
        val request = JsonUtils.parsePairRequest(postData)
        
        if (request == null) {
            Log.w(TAG, "❌ /pair invalid request format")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                JsonUtils.createPairResponse(false, "INVALID_FORMAT")
            )
        }
        
        val serverToken = prefsManager.getServerToken()
        
        // Verify token
        if (request.token != serverToken) {
            Log.w(TAG, "❌ /pair UNAUTHORIZED - token mismatch")
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                JsonUtils.createPairResponse(false, "UNAUTHORIZED")
            )
        }
        
        // Token valid - set paired state
        Log.d(TAG, "✅ /pair success - token valid, mainId=${request.mainId}, mainName=${request.mainName}")
        prefsManager.setDisplayPaired(true, request.mainId, request.mainName)
        
        // Broadcast pairing state change
        broadcastPairingChanged(true)
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JsonUtils.createPairResponse(true)
        )
    }
    
    private fun handleOpenUrl(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        
        val postData = body["postData"] ?: ""
        Log.d(TAG, "📨 /open-url request: $postData")
        
        val request = JsonUtils.parseOpenUrlRequest(postData)
        
        if (request == null) {
            Log.w(TAG, "❌ /open-url invalid request format")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                JsonUtils.createOpenUrlResponse(false, reason = "INVALID_FORMAT")
            )
        }
        
        val (token, url) = request
        val serverToken = prefsManager.getServerToken()
        
        // Check if paired first
        if (!prefsManager.isDisplayPaired()) {
            Log.w(TAG, "❌ /open-url NOT_PAIRED - device not paired")
            return newFixedLengthResponse(
                Response.Status.CONFLICT,
                "application/json",
                JsonUtils.createOpenUrlResponse(false, reason = "NOT_PAIRED")
            )
        }
        
        // Verify token
        if (token != serverToken) {
            Log.w(TAG, "❌ /open-url UNAUTHORIZED - token mismatch")
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                JsonUtils.createOpenUrlResponse(false, reason = "UNAUTHORIZED")
            )
        }
        
        // Token valid and paired - open Maps
        Log.d(TAG, "✅ /open-url authorized, opening URL: $url")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.google.android.apps.maps")
            }
            context.startActivity(intent)
            
            Log.d(TAG, "✅ Maps opened successfully")
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
                
                Log.d(TAG, "✅ URL opened with fallback browser")
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    JsonUtils.createOpenUrlResponse(true, "URL opened (fallback)")
                )
            } catch (e2: Exception) {
                Log.e(TAG, "💥 Fallback also failed", e2)
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    JsonUtils.createOpenUrlResponse(false, "Failed to open URL: ${e2.message}")
                )
            }
        }
    }
    
    private fun handleUnpair(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        
        val postData = body["postData"] ?: ""
        Log.d(TAG, "📨 /unpair request: $postData")
        
        // Parse to verify token if provided
        val json = try {
            org.json.JSONObject(postData)
        } catch (e: Exception) {
            null
        }
        
        val token = json?.optString("token", "")
        val serverToken = prefsManager.getServerToken()
        
        // If token provided, verify it
        if (!token.isNullOrEmpty() && token != serverToken) {
            Log.w(TAG, "❌ /unpair UNAUTHORIZED - token mismatch")
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                "application/json",
                JsonUtils.createErrorResponse("Invalid token", "UNAUTHORIZED")
            )
        }
        
        // Reset paired state
        Log.d(TAG, "✅ /unpair - resetting paired state")
        prefsManager.setDisplayPaired(false)
        
        // Broadcast pairing state change
        broadcastPairingChanged(false)
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JsonUtils.createOpenUrlResponse(true, "Unpaired successfully")
        )
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
        
        // Reset paired state when token refreshed
        prefsManager.setDisplayPaired(false)
        broadcastPairingChanged(false)
        
        val newToken = prefsManager.refreshServerToken()
        Log.d(TAG, "🔄 Token refreshed, new hint: ${prefsManager.getTokenHint(newToken)}")
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            JsonUtils.createRefreshTokenResponse(newToken)
        )
    }
    
    private fun broadcastPairingChanged(paired: Boolean) {
        try {
            val intent = Intent(ACTION_PAIRING_CHANGED).apply {
                putExtra("paired", paired)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "📢 Broadcasted PAIRING_CHANGED: paired=$paired")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to broadcast pairing change", e)
        }
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
