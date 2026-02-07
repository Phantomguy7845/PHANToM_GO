package com.phantom.carnavrelay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.random.Random

object DisplayModeFlow {
  fun start(ctx: Context): Boolean {
    // ตรวจสอบสิทธิ์ Bluetooth ก่อน
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val hasConnect = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
      val hasScan = ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
      if (!hasConnect || !hasScan) {
        MaterialAlertDialogBuilder(ctx)
          .setTitle("ต้องการสิทธิ์ Bluetooth")
          .setMessage("กรุณาอนุญาติสิทธิ์ Bluetooth เพื่อใช้งานโหมดนี้")
          .setPositiveButton(R.string.ok, null)
          .show()
        return false
      }
    }
    
    // ตรวจสอบ Bluetooth Adapter
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
    
    val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
    val i = Intent(ctx, DisplayActivity::class.java).apply {
      putExtra("code", code)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(i)
    return true
  }
}
