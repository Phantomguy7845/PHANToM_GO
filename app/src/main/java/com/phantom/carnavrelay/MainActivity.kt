package com.phantom.carnavrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

  private val BT_PERMISSION_REQUEST = 1001

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Check and request permissions first
    if (!hasBtPermissions()) {
      requestBtPermissions()
    } else {
      showModeSelection()
    }
  }

  private fun hasBtPermissions(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
           ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
  }

  private fun requestBtPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val permissions = mutableListOf<String>()
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
      }
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
      }
      if (permissions.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), BT_PERMISSION_REQUEST)
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == BT_PERMISSION_REQUEST) {
      if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
        showModeSelection()
      } else {
        showPermissionDeniedDialog()
      }
    }
  }

  private fun showPermissionDeniedDialog() {
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.bt_permission_title))
      .setMessage(getString(R.string.bt_permission_message))
      .setPositiveButton(R.string.ok) { _, _ -> finish() }
      .setCancelable(false)
      .show()
  }

  private fun showModeSelection() {
    val layout = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 48, 48, 48)
      gravity = android.view.Gravity.CENTER_HORIZONTAL
    }

    val title = TextView(this).apply {
      text = getString(R.string.app_name)
      textSize = 32f
      setTextColor(ContextCompat.getColor(context, R.color.purple_500))
      setPadding(0, 0, 0, 16)
      gravity = android.view.Gravity.CENTER
    }

    val subtitle = TextView(this).apply {
      text = getString(R.string.select_mode)
      textSize = 18f
      setPadding(0, 0, 0, 48)
      gravity = android.view.Gravity.CENTER
    }

    // Main Mode Card
    val mainCard = MaterialCardView(this).apply {
      radius = 24f
      cardElevation = 8f
      setContentPadding(32, 32, 32, 32)
      setCardBackgroundColor(ContextCompat.getColor(context, R.color.purple_500))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { setMargins(0, 0, 0, 24) }
    }

    val mainLayout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
    }

    val mainIcon = TextView(context).apply {
      text = "📱"
      textSize = 48f
      gravity = android.view.Gravity.CENTER
    }

    val mainText = TextView(context).apply {
      text = getString(R.string.mode_main)
      textSize = 20f
      setTextColor(ContextCompat.getColor(context, R.color.white))
      gravity = android.view.Gravity.CENTER
    }

    val mainDesc = TextView(context).apply {
      text = "ส่งตำแหน่งนำทางไปยังจอติดรถ"
      textSize = 14f
      setTextColor(ContextCompat.getColor(context, R.color.white))
      alpha = 0.8f
      gravity = android.view.Gravity.CENTER
    }

    mainLayout.addView(mainIcon)
    mainLayout.addView(mainText)
    mainLayout.addView(mainDesc)
    mainCard.addView(mainLayout)

    mainCard.setOnClickListener {
      startMainMode()
    }

    // Display Mode Card
    val displayCard = MaterialCardView(this).apply {
      radius = 24f
      cardElevation = 8f
      setContentPadding(32, 32, 32, 32)
      setCardBackgroundColor(ContextCompat.getColor(context, R.color.teal_700))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val displayLayout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
    }

    val displayIcon = TextView(context).apply {
      text = "🚗"
      textSize = 48f
      gravity = android.view.Gravity.CENTER
    }

    val displayText = TextView(context).apply {
      text = getString(R.string.mode_display)
      textSize = 20f
      setTextColor(ContextCompat.getColor(context, R.color.white))
      gravity = android.view.Gravity.CENTER
    }

    val displayDesc = TextView(context).apply {
      text = "แสดงตำแหน่งที่ได้รับจากเครื่องหลัก"
      textSize = 14f
      setTextColor(ContextCompat.getColor(context, R.color.white))
      alpha = 0.8f
      gravity = android.view.Gravity.CENTER
    }

    displayLayout.addView(displayIcon)
    displayLayout.addView(displayText)
    displayLayout.addView(displayDesc)
    displayCard.addView(displayLayout)

    displayCard.setOnClickListener {
      startDisplayMode()
    }

    layout.addView(title)
    layout.addView(subtitle)
    layout.addView(mainCard)
    layout.addView(displayCard)

    setContentView(layout)
  }

  private fun startMainMode() {
    MainModeFlow.start(this)
    finish()
  }

  private fun startDisplayMode() {
    DisplayModeFlow.start(this)
    finish()
  }
}
