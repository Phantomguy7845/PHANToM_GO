package com.phantom.carnavrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

  private var pendingMode: Int? = null // 0=MAIN, 1=DISPLAY

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setupModeCards()
  }

  private fun setupModeCards() {
    findViewById<CardView>(R.id.mainCard).setOnClickListener {
      pendingMode = 0
      ensureBtPermissionsThenRun()
    }

    findViewById<CardView>(R.id.displayCard).setOnClickListener {
      pendingMode = 1
      ensureBtPermissionsThenRun()
    }
  }

  private fun ensureBtPermissionsThenRun() {
    if (hasBtPermissions()) {
      runSelectedMode()
      return
    }
    requestBtRuntimePermissions()
  }

  private fun runSelectedMode() {
    val success = when (pendingMode) {
      0 -> MainModeFlow.start(this)
      1 -> DisplayModeFlow.start(this)
      else -> {
        Toast.makeText(this, "กรุณาเลือกโหมด", Toast.LENGTH_SHORT).show()
        false
      }
    }

    // Only finish if mode started successfully
    if (success) {
      finish()
    }
    // If not successful, stay on MainActivity so user can see the error dialog
  }

  private fun hasBtPermissions(): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    val connectOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val scanOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    return connectOk && scanOk
  }

  private fun requestBtRuntimePermissions() {
    if (Build.VERSION.SDK_INT < 31) return
    val needed = mutableListOf<String>()
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
      needed.add(Manifest.permission.BLUETOOTH_CONNECT)
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
      needed.add(Manifest.permission.BLUETOOTH_SCAN)

    if (needed.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1001) {
      if (hasBtPermissions()) {
        runSelectedMode()
      } else {
        showPermissionDeniedDialog()
      }
    }
  }

  private fun showPermissionDeniedDialog() {
    AlertDialog.Builder(this)
      .setTitle("ต้องอนุญาต Bluetooth")
      .setMessage("ถ้าไม่อนุญาต BLUETOOTH_CONNECT/SCAN แอปจะเชื่อมต่ออุปกรณ์ไม่ได้")
      .setPositiveButton("ลองใหม่") { _, _ ->
        ensureBtPermissionsThenRun()
      }
      .setNegativeButton("ปิด") { _, _ ->
        finish()
      }
      .setCancelable(false)
      .show()
  }
}
