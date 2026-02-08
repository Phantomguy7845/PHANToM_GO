package com.phantom.carnavrelay.util

import android.net.Uri
import android.util.Log

object NavLinkNormalizer {

    private const val TAG = "PHANTOM_GO"

    fun normalize(raw: String): String? {
        if (raw.isBlank()) return null

        // Try to parse as URI first
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            null
        }

        return when {
            // If it's a valid URI with scheme, use normalizeUri
            uri?.scheme != null -> normalizeUri(uri)
            // If it looks like coordinates (lat,lng), create geo URI
            raw.matches(Regex("""^-?\d+\.?\d*,-?\d+\.?\d*\$""")) -> {
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(raw)}"
            }
            // If it's a search query, create search URL
            else -> {
                "https://www.google.com/maps/search/?api=1&query=${Uri.encode(raw)}"
            }
        }
    }

    private fun normalizeUri(uri: Uri): String? {
        return when (uri.scheme) {
            "geo" -> {
                // geo:lat,lng or geo:0,0?q=...
                val geoData = uri.schemeSpecificPart
                val query = uri.query
                
                if (query != null && query.startsWith("q=")) {
                    // geo:0,0?q=search+query
                    val searchQuery = query.substring(2).replace("+", " ")
                    "https://www.google.com/maps/search/?api=1&query=${Uri.encode(searchQuery)}"
                } else if (geoData.contains(",")) {
                    // geo:lat,lng
                    val parts = geoData.split(",")
                    if (parts.size >= 2) {
                        val lat = parts[0]
                        val lng = parts[1].substringBefore(";") // Remove any additional params
                        "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
                    } else null
                } else null
            }
            
            "google.navigation" -> {
                // google.navigation:q=...
                val query = uri.getQueryParameter("q")
                if (query != null) {
                    "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(query)}"
                } else null
            }
            
            "https" -> {
                when {
                    uri.host?.contains("google.com") == true && uri.path?.contains("/maps") == true -> {
                        // Google Maps URL - use as-is
                        uri.toString()
                    }
                    uri.host?.contains("maps.app.goo.gl") == true -> {
                        // Shortened Google Maps URL - use as-is
                        uri.toString()
                    }
                    else -> uri.toString()
                }
            }
            
            else -> {
                Log.w(TAG, "Unsupported URI scheme: ${uri.scheme}")
                uri.toString()
            }
        }
    }
}
