package com.phantom.carnavrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

  private var pendingMode: Int? = null // 0=MAIN, 1=DISPLAY

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showModePicker()
  }

  private fun showModePicker() {
    AlertDialog.Builder(this)
      .setTitle("เลือกโหมดการใช้งาน")
      .setItems(arrayOf("เครื่องหลัก (Main)", "จอติดรถ (Display)")) { _, which ->
        pendingMode = which
        ensureBtPermissionsThenRun()
      }
      .setCancelable(true)
      .show()
  }

  private fun ensureBtPermissionsThenRun() {
    if (hasBtPermissions()) {
      runSelectedMode()
      return
    }
    requestBtRuntimePermissions()
  }

  private fun runSelectedMode() {
    when (pendingMode) {
      0 -> MainModeFlow.start(this)
      1 -> DisplayModeFlow.start(this)
      else -> {}
    }
    finish()
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

    ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 1001) {
      if (hasBtPermissions()) runSelectedMode()
      else {
        AlertDialog.Builder(this)
          .setTitle("ต้องอนุญาต Bluetooth")
          .setMessage("ถ้าไม่อนุญาต BLUETOOTH_CONNECT/SCAN แอปจะเชื่อมต่ออุปกรณ์ไม่ได้")
          .setPositiveButton("ลองใหม่") { _, _ ->
            ensureBtPermissionsThenRun()
          }
          .setNegativeButton("ปิด") { _, _ -> finish() }
          .show()
      }
    }
  }
}
