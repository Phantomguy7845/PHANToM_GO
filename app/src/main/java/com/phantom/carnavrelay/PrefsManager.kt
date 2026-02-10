package com.phantom.carnavrelay

import android.content.Context
import android.content.SharedPreferences
import com.phantom.carnavrelay.util.generateTokenHex

class PrefsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getPrefs(): SharedPreferences = prefs
    
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

        // Device mode
        private const val KEY_DEVICE_MODE = "device_mode"
        const val MODE_MAIN = "MAIN"
        const val MODE_DISPLAY = "DISPLAY"

        // Map link hub toggle
        private const val KEY_MAP_LINK_HUB_ENABLED = "map_link_hub_enabled"

        // Smart mode toggle + policy
        private const val KEY_SMART_MODE_ENABLED = "smart_mode_enabled"
        private const val KEY_SMART_MODE_POLICY = "smart_mode_policy"

        const val SMART_POLICY_AUTO_SEND_NAV_ONLY = "AUTO_SEND_NAV_ONLY"
        const val SMART_POLICY_ALWAYS_ASK = "ALWAYS_ASK"
        const val SMART_POLICY_ALWAYS_SEND = "ALWAYS_SEND"
        const val SMART_POLICY_ALWAYS_OPEN_ON_PHONE = "ALWAYS_OPEN_ON_PHONE"

        // After send behavior
        private const val KEY_AFTER_SEND_BEHAVIOR = "after_send_behavior"
        const val AFTER_SEND_EXIT_AND_REMOVE_TASK = "EXIT_AND_REMOVE_TASK"
        const val AFTER_SEND_STAY_IN_APP = "STAY_IN_APP"

        // Open Maps after send (optional)
        private const val KEY_OPEN_MAPS_AFTER_SEND = "open_maps_after_send"

        // Short link resolve cache
        private const val KEY_URL_RESOLVE_CACHE = "url_resolve_cache"
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

    fun getDeviceMode(): String {
        return prefs.getString(KEY_DEVICE_MODE, MODE_MAIN) ?: MODE_MAIN
    }

    fun setDeviceMode(mode: String) {
        prefs.edit().putString(KEY_DEVICE_MODE, mode).apply()
    }

    fun isMapLinkHubEnabled(): Boolean {
        return prefs.getBoolean(KEY_MAP_LINK_HUB_ENABLED, false)
    }

    fun setMapLinkHubEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MAP_LINK_HUB_ENABLED, enabled).apply()
    }

    fun isSmartModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_SMART_MODE_ENABLED, false)
    }

    fun setSmartModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_MODE_ENABLED, enabled).apply()
    }

    fun getSmartModePolicy(): String {
        return prefs.getString(KEY_SMART_MODE_POLICY, SMART_POLICY_AUTO_SEND_NAV_ONLY)
            ?: SMART_POLICY_AUTO_SEND_NAV_ONLY
    }

    fun setSmartModePolicy(policy: String) {
        prefs.edit().putString(KEY_SMART_MODE_POLICY, policy).apply()
    }

    fun getAfterSendBehavior(): String {
        return prefs.getString(KEY_AFTER_SEND_BEHAVIOR, AFTER_SEND_EXIT_AND_REMOVE_TASK)
            ?: AFTER_SEND_EXIT_AND_REMOVE_TASK
    }

    fun setAfterSendBehavior(behavior: String) {
        prefs.edit().putString(KEY_AFTER_SEND_BEHAVIOR, behavior).apply()
    }

    fun isOpenMapsAfterSendEnabled(): Boolean {
        return prefs.getBoolean(KEY_OPEN_MAPS_AFTER_SEND, false)
    }

    fun setOpenMapsAfterSendEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_MAPS_AFTER_SEND, enabled).apply()
    }

    fun getResolvedUrlFor(shortUrl: String): String? {
        val json = prefs.getString(KEY_URL_RESOLVE_CACHE, "{}") ?: "{}"
        return try {
            val obj = org.json.JSONObject(json)
            obj.optString(shortUrl, null)
        } catch (e: Exception) {
            null
        }
    }

    fun putResolvedUrl(shortUrl: String, resolvedUrl: String) {
        val json = prefs.getString(KEY_URL_RESOLVE_CACHE, "{}") ?: "{}"
        val obj = try {
            org.json.JSONObject(json)
        } catch (e: Exception) {
            org.json.JSONObject()
        }
        obj.put(shortUrl, resolvedUrl)
        prefs.edit().putString(KEY_URL_RESOLVE_CACHE, obj.toString()).apply()
    }
    
    private fun generateNewToken(): String {
        return generateTokenHex(12) // 24 hex chars, URL-safe
    }
    
    // Get token hint (first 4 + last 4 chars)
    fun getTokenHint(token: String): String {
        if (token.length <= 8) return token
        return "${token.take(4)}...${token.takeLast(4)}"
    }
}
