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

  private val PERMISSION_REQUEST_CODE = 1001

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Check and request all permissions first
    if (!hasAllPermissions()) {
      requestAllPermissions()
    } else {
      showModeSelection()
    }
  }

  private fun hasAllPermissions(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      // Android 12 ขึ้นไปเท่านั้นที่ต้องขอ Bluetooth permissions
      return true
    }
    
    val hasBtConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val hasBtScan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    
    // Android 13+ ต้องขอ Notification permission
    val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    
    return hasBtConnect && hasBtScan && hasNotification
  }

  private fun requestAllPermissions() {
    val permissions = mutableListOf<String>()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
      }
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
      }
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    
    if (permissions.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == PERMISSION_REQUEST_CODE) {
      if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
        showModeSelection()
      } else {
        showPermissionDeniedDialog()
      }
    }
  }

  private fun showPermissionDeniedDialog() {
    AlertDialog.Builder(this)
      .setTitle("ต้องการสิทธิ์ที่จำเป็น")
      .setMessage("แอปต้องการสิทธิ์ Bluetooth และ Notifications เพื่อทำงานได้อย่างถูกต้อง กรุณาอนุญาติสิทธิ์ใน Settings")
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
      setTextColor(ContextCompat.getColor(this@MainActivity, R.color.purple_500))
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
      setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.purple_500))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ).apply { setMargins(0, 0, 0, 24) }
    }

    val mainLayout = LinearLayout(this@MainActivity).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
    }

    val mainIcon = TextView(this@MainActivity).apply {
      text = "📱"
      textSize = 48f
      gravity = android.view.Gravity.CENTER
    }

    val mainText = TextView(this@MainActivity).apply {
      text = getString(R.string.mode_main)
      textSize = 20f
      setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
      gravity = android.view.Gravity.CENTER
    }

    val mainDesc = TextView(this@MainActivity).apply {
      text = "ส่งตำแหน่งนำทางไปยังจอติดรถ"
      textSize = 14f
      setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
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
      setCardBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.teal_700))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    val displayLayout = LinearLayout(this@MainActivity).apply {
      orientation = LinearLayout.VERTICAL
      gravity = android.view.Gravity.CENTER
    }

    val displayIcon = TextView(this@MainActivity).apply {
      text = "🚗"
      textSize = 48f
      gravity = android.view.Gravity.CENTER
    }

    val displayText = TextView(this@MainActivity).apply {
      text = getString(R.string.mode_display)
      textSize = 20f
      setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
      gravity = android.view.Gravity.CENTER
    }

    val displayDesc = TextView(this@MainActivity).apply {
      text = "แสดงตำแหน่งที่ได้รับจากเครื่องหลัก"
      textSize = 14f
      setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
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
    // Removed finish() - let the dialog show properly
  }

  private fun startDisplayMode() {
    val started = DisplayModeFlow.start(this)
    if (started) {
      // ปิด MainActivity เมื่อ DisplayActivity เปิดสำเร็จ
      finish()
    }
  }
}
