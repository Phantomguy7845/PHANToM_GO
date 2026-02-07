package com.phantom.carnavrelay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object MainModeFlow {
  fun start(ctx: Context): Boolean {
    // Check Bluetooth permissions first
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val hasConnect = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
      val hasScan = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
      if (!hasConnect || !hasScan) {
        showPermissionDialog(ctx)
        return false
      }
    }

    val adapter = BluetoothAdapter.getDefaultAdapter()
    
    if (adapter == null) {
      MaterialAlertDialogBuilder(ctx)
        .setTitle("ไม่รองรับ Bluetooth")
        .setMessage("อุปกรณ์นี้ไม่รองรับ Bluetooth")
        .setPositiveButton(R.string.ok, null)
        .show()
      return false
    }
    
    if (!adapter.isEnabled) {
      MaterialAlertDialogBuilder(ctx)
        .setTitle("Bluetooth ปิดอยู่")
        .setMessage("กรุณาเปิด Bluetooth ใน Settings ก่อนใช้งาน")
        .setPositiveButton(R.string.ok, null)
        .show()
      return false
    }
    
    val bonded = adapter.bondedDevices?.toList().orEmpty()

    if (bonded.isEmpty()) {
      MaterialAlertDialogBuilder(ctx)
        .setTitle(R.string.paired_devices)
        .setMessage(R.string.no_paired_devices)
        .setPositiveButton(R.string.ok, null)
        .show()
      return false
    }

    val names = bonded.map { "${it.name ?: "Unknown"}\n${it.address}" }.toTypedArray()

    MaterialAlertDialogBuilder(ctx)
      .setTitle(R.string.paired_devices)
      .setItems(names) { _, which ->
        val device = bonded[which]
        // Start MainPairActivity instead of showing dialog
        val intent = Intent(ctx, MainPairActivity::class.java).apply {
          putExtra("address", device.address)
          putExtra("device_name", device.name ?: "Unknown")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
      }
      .setNegativeButton(R.string.cancel, null)
      .show()
    
    return true
  }

  private fun showPermissionDialog(ctx: Context) {
    MaterialAlertDialogBuilder(ctx)
      .setTitle("ต้องการสิทธิ์ Bluetooth")
      .setMessage("กรุณาอนุญาติสิทธิ์ Bluetooth เพื่อใช้งานโหมดนี้")
      .setPositiveButton(R.string.ok, null)
      .show()
  }
}
