package com.phantom.carnavrelay.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phantom.carnavrelay.PrefsManager
import com.phantom.carnavrelay.MainSender
import com.phantom.carnavrelay.util.NavLinkNormalizer

class NavCaptureAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PHANTOM_GO"
    }

    // กันยิงซ้ำถี่ ๆ เวลา UI กระพริบ
    private var lastSent: String? = null
    private var lastSentAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
        Log.d(TAG, "A11Y connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // สวิตช์ในแอป (เปิด/ปิดการทำงาน) — ถึงแม้ service ยัง enabled อยู่
        if (!PrefsManager(this).isA11yCaptureEnabled()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleClick(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // optional: ถ้าอยากจับตอนแอปเปิดหน้าจอ maps ก็ใส่เพิ่มได้
            }
        }
    }

    private fun handleClick(event: AccessibilityEvent) {
        val src = event.source ?: return

        val raw = extractPossibleLink(src, maxDepth = 3)
            ?: event.text?.joinToString(" ")?.takeIf { 
                it.contains("http", true) || it.contains("geo:", true) || it.contains("google.navigation", true) 
            }

        if (raw.isNullOrBlank()) return

        val mapsUrl = NavLinkNormalizer.normalize(raw) ?: return

        // throttle กันส่งซ้ำ
        val now = System.currentTimeMillis()
        if (mapsUrl == lastSent && now - lastSentAt < 1500) return
        lastSent = mapsUrl
        lastSentAt = now

        Log.d(TAG, "A11Y captured -> $mapsUrl")

        // ส่งไปจอด้วยช่องทางเดียวกับปุ่ม Send Test Location (ของคุณ)
        MainSender.sendOpenUrlAsync(
            context = applicationContext,
            mapsUrl = mapsUrl,
            source = "a11y"
        )
    }

    /**
     * พยายามดึง "ข้อความที่เป็นลิงก์/พิกัด" จาก node ที่ถูกคลิกและรอบ ๆ
     * maxDepth จำกัดเพื่อกันหนัก/ช้า
     */
    private fun extractPossibleLink(node: AccessibilityNodeInfo, maxDepth: Int): String? {
        fun pick(n: AccessibilityNodeInfo): String? {
            val t = n.text?.toString()?.trim()
            val d = n.contentDescription?.toString()?.trim()
            val c = listOf(t, d).firstOrNull { !it.isNullOrBlank() } ?: return null
            val s = c.lowercase()
            val hit = s.contains("http") || s.contains("geo:") || s.contains("google.navigation") || s.contains("maps.app.goo.gl")
            return if (hit) c else null
        }

        // 1) ตัวมันเอง
        pick(node)?.let { return it }

        // 2) ลูก ๆ (เช่น ข้อความอยู่ใน child)
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val ch = node.getChild(i) ?: continue
            pick(ch)?.let { return it }
        }

        // 3) ไล่ parent (เช่น คลิกปุ่ม แต่ url อยู่ในการ์ด)
        var p: AccessibilityNodeInfo? = node
        var depth = 0
        while (depth < maxDepth) {
            p = p?.parent ?: break
            pick(p)?.let { return it }
            depth++
        }

        return null
    }

    override fun onInterrupt() {
        // no-op
    }
}
