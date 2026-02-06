package com.phantom.carnavrelay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    requestBtRuntimePermissions()

    AlertDialog.Builder(this)
      .setTitle("เลือกโหมดการใช้งาน")
      .setItems(arrayOf("เครื่องหลัก (Main)", "จอติดรถ (Display)")) { _, which ->
        if (which == 0) MainModeFlow.start(this)
        else DisplayModeFlow.start(this)
        finish()
      }
      .setCancelable(true)
      .show()
  }

  private fun requestBtRuntimePermissions() {
    if (Build.VERSION.SDK_INT < 31) return
    val needed = mutableListOf<String>()
    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
      needed.add(Manifest.permission.BLUETOOTH_CONNECT)
    if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
      needed.add(Manifest.permission.BLUETOOTH_SCAN)

    if (needed.isNotEmpty()) {
      ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
    }
  }
}
