package com.phantom.carnavrelay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.phantom.carnavrelay.bt.MainClientService

class RelayActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val data = intent?.data
    if (data == null) { finish(); return }

    val url = NavNormalizer.normalizeToMapsUrl(data)
    if (url.isNullOrBlank()) { finish(); return }

    startService(Intent(this, MainClientService::class.java).apply {
      putExtra("send_url", url)
      putExtra("mode", "SEND_ONLY")
    })

    finish()
  }
}
