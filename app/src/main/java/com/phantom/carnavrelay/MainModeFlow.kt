package com.phantom.carnavrelay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object MainModeFlow {

  private const val TAG = "PHANTOM_GO"

  fun start(ctx: Context): Boolean {
    Log.d(TAG, "▶️ MainModeFlow.start() called")
    
    // Check Bluetooth permissions first
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val hasConnect = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
      val hasScan = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
      Log.d(TAG, "🔒 BT permissions - CONNECT: $hasConnect, SCAN: $hasScan")
      
      if (!hasConnect || !hasScan) {
        Log.w(TAG, "❌ Missing Bluetooth permissions for MainMode")
        showPermissionDialog(ctx)
        return false
      }
    }

    val adapter = BluetoothAdapter.getDefaultAdapter()
    
    if (adapter == null) {
      Log.e(TAG, "❌ No Bluetooth adapter found")
      MaterialAlertDialogBuilder(ctx)
        .setTitle("ไม่รองรับ Bluetooth")
        .setMessage("อุปกรณ์นี้ไม่รองรับ Bluetooth")
        .setPositiveButton(R.string.ok, null)
        .show()
      return false
    }
    
    if (!adapter.isEnabled) {
      Log.w(TAG, "⚠️ Bluetooth is disabled")
      MaterialAlertDialogBuilder(ctx)
        .setTitle("Bluetooth ปิดอยู่")
        .setMessage("กรุณาเปิด Bluetooth ใน Settings ก่อนใช้งาน")
        .setPositiveButton(R.string.ok, null)
        .show()
      return false
    }
    
    val bonded = try {
      adapter.bondedDevices?.toList().orEmpty()
    } catch (e: SecurityException) {
      Log.e(TAG, "💥 SecurityException accessing bonded devices", e)
      CrashReporter.recordException(ctx, "MainModeFlow:getBondedDevices", e)
      emptyList()
    }
    
    Log.d(TAG, "🔗 Found ${bonded.size} paired device(s)")

    if (bonded.isEmpty()) {
      Log.w(TAG, "⚠️ No paired Bluetooth devices")
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
        Log.d(TAG, "📱 User selected device: ${device.name} (${device.address})")
        
        // Start MainPairActivity instead of showing dialog
        val intent = Intent(ctx, MainPairActivity::class.java).apply {
          putExtra("address", device.address)
          putExtra("device_name", device.name ?: "Unknown")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
          ctx.startActivity(intent)
          Log.d(TAG, "✅ MainPairActivity started")
        } catch (e: Exception) {
          Log.e(TAG, "💥 Failed to start MainPairActivity", e)
          CrashReporter.recordException(ctx, "MainModeFlow:startMainPairActivity", e)
          Toast.makeText(ctx, "ไม่สามารถเปิดหน้าจอคู่เครื่องได้: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
