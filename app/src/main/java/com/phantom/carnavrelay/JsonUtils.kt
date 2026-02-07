package com.phantom.carnavrelay

import org.json.JSONObject
import org.json.JSONArray

object JsonUtils {
    
    fun createStatusResponse(ip: String, port: Int, paired: Boolean, tokenHint: String, version: String = "1.0"): String {
        return JSONObject().apply {
            put("ok", true)
            put("ip", ip)
            put("port", port)
            put("paired", paired)
            put("tokenHint", tokenHint)
            put("version", version)
        }.toString()
    }
    
    fun createOpenUrlResponse(success: Boolean, message: String = ""): String {
        return JSONObject().apply {
            put("ok", success)
            if (message.isNotEmpty()) {
                put("message", message)
            }
        }.toString()
    }
    
    fun createRefreshTokenResponse(newToken: String): String {
        return JSONObject().apply {
            put("ok", true)
            put("token", newToken)
        }.toString()
    }
    
    fun createErrorResponse(message: String): String {
        return JSONObject().apply {
            put("ok", false)
            put("message", message)
        }.toString()
    }
    
    fun parseOpenUrlRequest(body: String): Pair<String, String>? {
        return try {
            val json = JSONObject(body)
            val token = json.getString("token")
            val url = json.getString("url")
            Pair(token, url)
        } catch (e: Exception) {
            null
        }
    }
    
    fun createPairingPayload(ip: String, port: Int, token: String): String {
        return JSONObject().apply {
            put("ip", ip)
            put("port", port)
            put("token", token)
        }.toString()
    }
    
    fun parsePairingPayload(payload: String): Triple<String, Int, String>? {
        return try {
            val json = JSONObject(payload)
            val ip = json.getString("ip")
            val port = json.getInt("port")
            val token = json.getString("token")
            Triple(ip, port, token)
        } catch (e: Exception) {
            null
        }
    }
    
    fun createPendingQueueJson(urls: List<String>): String {
        return JSONArray(urls).toString()
    }
    
    fun parsePendingQueueJson(json: String): List<String> {
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
