package com.phantom.carnavrelay

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incoming = intent ?: run {
            finish()
            return
        }

        val forward = Intent(this, RelayActivity::class.java).apply {
            action = incoming.action
            type = incoming.type
            data = incoming.data
            clipData = incoming.clipData
            putExtras(incoming)
        }

        startActivity(forward)
        finish()
        overridePendingTransition(0, 0)
    }
}
