package com.phantom.carnavrelay

import android.net.Uri

object NavNormalizer {
  fun normalizeToMapsUrl(data: Uri): String? {
    val scheme = data.scheme ?: return null
    return when (scheme) {
      "geo" -> fromGeo(data)
      "google.navigation" -> fromGoogleNav(data)
      "http", "https" -> data.toString()
      else -> null
    }
  }

  private fun fromGeo(uri: Uri): String? {
    val q = uri.getQueryParameter("q")
    if (!q.isNullOrBlank()) {
      return "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(q)}"
    }
    val ssp = uri.schemeSpecificPart ?: return null
    val latLng = ssp.substringBefore("?").trim()
    if (latLng.contains(",")) {
      return "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(latLng)}"
    }
    return null
  }

  private fun fromGoogleNav(uri: Uri): String? {
    val q = uri.getQueryParameter("q") ?: return null
    return "https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(q)}"
  }
}
