package com.phantom.carnavrelay

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "phantom_go_prefs"
        
        // Display device settings
        private const val KEY_SERVER_TOKEN = "server_token"
        private const val KEY_SERVER_PORT = "server_port"
        
        // Display device: paired state
        private const val KEY_DISPLAY_PAIRED = "display_paired"
        private const val KEY_DISPLAY_PAIRED_MAIN_ID = "display_paired_main_id"
        private const val KEY_DISPLAY_PAIRED_MAIN_NAME = "display_paired_main_name"
        private const val KEY_DISPLAY_PAIRED_AT = "display_paired_at"
        
        // Main device paired config
        private const val KEY_PAIRED_IP = "paired_ip"
        private const val KEY_PAIRED_PORT = "paired_port"
        private const val KEY_PAIRED_TOKEN = "paired_token"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_PAIRED_VERIFIED = "paired_verified"  // Server-verified status
        
        // Pending queue
        private const val KEY_PENDING_QUEUE = "pending_queue"
    }
    
    // Display device: Server token
    fun getServerToken(): String {
        return prefs.getString(KEY_SERVER_TOKEN, null) ?: generateNewToken()
    }
    
    fun setServerToken(token: String) {
        prefs.edit().putString(KEY_SERVER_TOKEN, token).apply()
    }
    
    fun refreshServerToken(): String {
        val newToken = generateNewToken()
        setServerToken(newToken)
        // Reset paired state when token refreshed
        setDisplayPaired(false)
        return newToken
    }
    
    // Display device: Server port (default 8765)
    fun getServerPort(): Int {
        return prefs.getInt(KEY_SERVER_PORT, 8765)
    }
    
    fun setServerPort(port: Int) {
        prefs.edit().putInt(KEY_SERVER_PORT, port).apply()
    }
    
    // Display device: Paired state
    fun isDisplayPaired(): Boolean {
        return prefs.getBoolean(KEY_DISPLAY_PAIRED, false)
    }
    
    fun setDisplayPaired(paired: Boolean, mainId: String? = null, mainName: String? = null) {
        prefs.edit().apply {
            putBoolean(KEY_DISPLAY_PAIRED, paired)
            if (paired) {
                putString(KEY_DISPLAY_PAIRED_MAIN_ID, mainId)
                putString(KEY_DISPLAY_PAIRED_MAIN_NAME, mainName)
                putLong(KEY_DISPLAY_PAIRED_AT, System.currentTimeMillis())
            } else {
                remove(KEY_DISPLAY_PAIRED_MAIN_ID)
                remove(KEY_DISPLAY_PAIRED_MAIN_NAME)
                remove(KEY_DISPLAY_PAIRED_AT)
            }
            apply()
        }
    }
    
    fun getDisplayPairedMainId(): String? {
        return prefs.getString(KEY_DISPLAY_PAIRED_MAIN_ID, null)
    }
    
    fun getDisplayPairedMainName(): String? {
        return prefs.getString(KEY_DISPLAY_PAIRED_MAIN_NAME, null)
    }
    
    fun getDisplayPairedAt(): Long {
        return prefs.getLong(KEY_DISPLAY_PAIRED_AT, 0)
    }
    
    fun clearDisplayPairing() {
        setDisplayPaired(false)
    }
    
    // Main device: Pairing config
    fun getPairedIp(): String? {
        return prefs.getString(KEY_PAIRED_IP, null)
    }
    
    fun getPairedPort(): Int {
        return prefs.getInt(KEY_PAIRED_PORT, 8765)
    }
    
    fun getPairedToken(): String? {
        return prefs.getString(KEY_PAIRED_TOKEN, null)
    }
    
    fun isPaired(): Boolean {
        return prefs.getBoolean(KEY_IS_PAIRED, false) && 
               !getPairedIp().isNullOrEmpty() && 
               !getPairedToken().isNullOrEmpty()
    }
    
    fun isPairedVerified(): Boolean {
        return isPaired() && prefs.getBoolean(KEY_PAIRED_VERIFIED, false)
    }
    
    fun setPairedVerified(verified: Boolean) {
        prefs.edit().putBoolean(KEY_PAIRED_VERIFIED, verified).apply()
    }
    
    fun savePairing(ip: String, port: Int, token: String, verified: Boolean = false) {
        prefs.edit().apply {
            putString(KEY_PAIRED_IP, ip)
            putInt(KEY_PAIRED_PORT, port)
            putString(KEY_PAIRED_TOKEN, token)
            putBoolean(KEY_IS_PAIRED, true)
            putBoolean(KEY_PAIRED_VERIFIED, verified)
            apply()
        }
    }
    
    fun clearPairing() {
        prefs.edit().apply {
            remove(KEY_PAIRED_IP)
            remove(KEY_PAIRED_PORT)
            remove(KEY_PAIRED_TOKEN)
            putBoolean(KEY_IS_PAIRED, false)
            putBoolean(KEY_PAIRED_VERIFIED, false)
            apply()
        }
    }
    
    // Pending queue for failed sends
    fun getPendingQueue(): List<String> {
        val json = prefs.getString(KEY_PENDING_QUEUE, "[]") ?: "[]"
        return JsonUtils.parsePendingQueueJson(json)
    }
    
    fun addToPendingQueue(url: String) {
        val queue = getPendingQueue().toMutableList()
        queue.add(url)
        savePendingQueue(queue)
    }
    
    fun removeFromPendingQueue(url: String) {
        val queue = getPendingQueue().toMutableList()
        queue.remove(url)
        savePendingQueue(queue)
    }
    
    fun savePendingQueue(queue: List<String>) {
        val json = JsonUtils.createPendingQueueJson(queue)
        prefs.edit().putString(KEY_PENDING_QUEUE, json).apply()
    }
    
    fun clearPendingQueue() {
        prefs.edit().remove(KEY_PENDING_QUEUE).apply()
    }
    
    fun getPendingCount(): Int {
        return getPendingQueue().size
    }
    
    private fun generateNewToken(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..24)
            .map { allowedChars.random() }
            .joinToString("")
    }
    
    // Get token hint (first 4 + last 4 chars)
    fun getTokenHint(token: String): String {
        if (token.length <= 8) return token
        return "${token.take(4)}...${token.takeLast(4)}"
    }
}
