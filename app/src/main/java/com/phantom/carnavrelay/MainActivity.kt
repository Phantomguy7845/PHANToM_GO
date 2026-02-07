package com.phantom.carnavrelay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

  companion object {
    const val TAG = "PHANTOM_GO"
  }

  private var pendingMode: Int? = null // 0=MAIN, 1=DISPLAY

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "🏠 MainActivity onCreate")
    setContentView(R.layout.activity_main)
    
    // Check for pending crash reports
    CrashReporter.checkAndShowCrashReport(this)
    
    setupModeCards()
  }

  private fun setupModeCards() {
    Log.d(TAG, "🎮 Setting up mode cards")
    
    findViewById<CardView>(R.id.mainCard).setOnClickListener {
      Log.d(TAG, "▶️ Main mode card clicked")
      pendingMode = 0
      ensureBtPermissionsThenRun()
    }

    findViewById<CardView>(R.id.displayCard).setOnClickListener {
      Log.d(TAG, "📺 Display mode card clicked")
      pendingMode = 1
      ensureBtPermissionsThenRun()
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_diagnostics -> {
        Log.d(TAG, "🔧 Opening Diagnostics")
        startActivity(Intent(this, DiagnosticsActivity::class.java))
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  private fun ensureBtPermissionsThenRun() {
    Log.d(TAG, "🔒 Checking Bluetooth permissions...")
    if (hasBtPermissions()) {
      Log.d(TAG, "✅ Bluetooth permissions granted")
      runSelectedMode()
      return
    }
    Log.w(TAG, "⚠️ Bluetooth permissions needed, requesting...")
    requestBtRuntimePermissions()
  }

  private fun runSelectedMode() {
    Log.d(TAG, "🚀 Running selected mode: $pendingMode")
    
    val success = try {
      when (pendingMode) {
        0 -> MainModeFlow.start(this)
        1 -> DisplayModeFlow.start(this)
        else -> {
          Log.w(TAG, "❓ Unknown mode selected: $pendingMode")
          Toast.makeText(this, "กรุณาเลือกโหมด", Toast.LENGTH_SHORT).show()
          false
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "💥 Exception starting mode $pendingMode", e)
      // Let CrashReporter catch this, but also show user-friendly message
      showCrashDialog(e)
      false
    }

    // Only finish if mode started successfully
    if (success) {
      Log.d(TAG, "✅ Mode started successfully, finishing MainActivity")
      finish()
    } else {
      Log.w(TAG, "⚠️ Mode start failed, staying on MainActivity")
    }
  }

  private fun showCrashDialog(e: Exception) {
    AlertDialog.Builder(this)
      .setTitle("เกิดข้อผิดพลาด")
      .setMessage("แอพพบปัญขณะเริ่มโหมด:\n\n${e.message}\n\nกรุณาลองใหม่หรือตรวจสอบ Diagnostics")
      .setPositiveButton("เปิด Diagnostics") { _, _ ->
        startActivity(Intent(this, DiagnosticsActivity::class.java))
      }
      .setNegativeButton("ปิด", null)
      .show()
  }

  private fun hasBtPermissions(): Boolean {
    if (Build.VERSION.SDK_INT < 31) {
      Log.d(TAG, "📱 SDK < 31, no runtime BT permissions needed")
      return true
    }
    val connectOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val scanOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    
    Log.d(TAG, "🔍 BT_CONNECT: $connectOk, BT_SCAN: $scanOk")
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
      Log.d(TAG, "📣 Requesting permissions: $needed")
      ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    Log.d(TAG, "📋 onRequestPermissionsResult: code=$requestCode, results=${grantResults.toList()}")
    
    if (requestCode == 1001) {
      if (hasBtPermissions()) {
        Log.d(TAG, "✅ Permissions granted after request")
        runSelectedMode()
      } else {
        Log.w(TAG, "❌ Permissions denied after request")
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

  override fun onResume() {
    super.onResume()
    Log.d(TAG, "▶️ MainActivity onResume")
  }

  override fun onPause() {
    super.onPause()
    Log.d(TAG, "⏸️ MainActivity onPause")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.d(TAG, "💀 MainActivity onDestroy")
  }
}
