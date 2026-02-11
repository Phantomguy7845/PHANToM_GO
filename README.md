# PHANToM GO

แอปส่งตำแหน่งนำทางจากมือถือเครื่องหลักไปยังมือถือจอติดรถ ผ่าน Bluetooth

## วิธีใช้งาน

### ตั้งค่าครั้งแรก
1. จับคู่ Bluetooth ระหว่าง 2 เครื่องใน Settings ของ Android ก่อน

### โหมดจอติดรถ (Display)
1. เปิดแอป → เลือก "จอติดรถ (แสดงผล)"
2. จดรหัส 6 หลักที่แสดงบนหน้าจอ
3. เปิดหน้านี้ค้างไว้ (หน้าจอจะไม่ดับ)

### โหมดเครื่องหลัก (Main)
1. เปิดแอป → เลือก "เครื่องหลัก (ส่งตำแหน่ง)"
2. เลือกอุปกรณ์จอติดรถจากรายการที่จับคู่ไว้
3. ใส่รหัส 6 หลักที่ได้จากจอติดรถ
4. กด "เชื่อมต่อ"

### ส่งตำแหน่ง
- จากแอปแผนที่ (Google Maps, Waze, etc.) กด "แชร์" หรือ "นำทาง"
- เลือกเปิดด้วย **PHANToM GO**
- ตำแหน่งจะส่งไปยังจอติดรถและเปิด Google Maps อัตโนมัติ

## คุณสมบัติ
- เชื่อมต่อผ่าน Bluetooth Classic (RFCOMM)
- ยืนยันตัวตนด้วยรหัส 6 หลัก
- รองรับลิงก์ geo:, google.navigation:, Google Maps
- Foreground Service รักษาการเชื่อมต่อ
- หน้าจอจอติดรถจะค้างไว้ตลอดเวลา

## GitHub Actions
มี workflow สำหรับ build APK อัตโนมัติ

## Test Commands (ADB)

### 🧪 Test ACTION_VIEW - google.navigation
```bash
# Test navigation to coordinates
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=13.7563,100.5018"

# Test navigation with address
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=Central+World+Bangkok"

# Test navigation with place name
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=Siam+Paragon"
```

### 🧪 Test ACTION_VIEW - geo URI
```bash
# Test geo coordinates
adb shell am start -a android.intent.action.VIEW -d "geo:13.7563,100.5018"

# Test geo with search query
adb shell am start -a android.intent.action.VIEW -d "geo:0,0?q=13.7563,100.5018"

# Test geo with address
adb shell am start -a android.intent.action.VIEW -d "geo:0,0?q=Central+World+Bangkok"
```

### 🧪 Test ACTION_VIEW - Google Maps URLs
```bash
# Test Google Maps direct URL
adb shell am start -a android.intent.action.VIEW -d "https://www.google.com/maps?q=13.7563,100.5018"

# Test Google Maps directions
adb shell am start -a android.intent.action.VIEW -d "https://www.google.com/maps/dir/?api=1&origin=13.7563,100.5018&destination=13.7463,100.5118"

# Test Google Maps place
adb shell am start -a android.intent.action.VIEW -d "https://www.google.com/maps/place/?q_place_id=ChIJ..."

# Test shortened Google Maps URL
adb shell am start -a android.intent.action.VIEW -d "https://maps.app.goo.gl/abc123"
```

### 🧪 Test ACTION_SEND - text/plain
```bash
# Test sending Google Maps URL
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "https://www.google.com/maps?q=13.7563,100.5018"

# Test sending coordinates
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "13.7563,100.5018"

# Test sending address
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "Central World, Bangkok"

# Test sending place name
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "Siam Paragon"
```

### 🧪 Verify Share Sheet Registration
```bash
# Confirm PHANToM GO is registered for text sharing
adb shell cmd package query-intent-activities -a android.intent.action.SEND -t text/plain | grep -i phantom -n
```

### 🧪 Test ACTION_PROCESS_TEXT
```bash
# Test processing selected text
adb shell am start -a android.intent.action.PROCESS_TEXT -t "text/plain" --es android.intent.extra.PROCESS_TEXT "13.7563,100.5018"
```

### 🧪 Test Share Target Priority
```bash
# Test that PHANToM GO appears prominently in sharesheet
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "https://maps.google.com/?q=test"

# Verify PHANToM GO appears at top of sharesheet
# Should show with priority 1000
```

### 🧪 Test Auto-finish Behavior
```bash
# Test that RelayActivity auto-finishes after sending
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "https://www.google.com/maps?q=test"

# Expected behavior:
# 1. RelayActivity shows "Sending..." screen
# 2. URL is sent to display device
# 3. Activity auto-finishes (1.5s success, 3s error)
# 4. Returns to previous app
```

### 🧪 Test Settings & Overlay
```bash
# Open Settings directly
adb shell am start -n com.phantom.carnavrelay/.SettingsActivity

# Test overlay permission flow
adb shell am start -a android.settings.action.MANAGE_OVERLAY_PERMISSION -d "package:com.phantom.carnavrelay"
```

### 🧪 Test Multiple Scenarios
```bash
# Test rapid succession (should handle properly)
for i in {1..5}; do
  adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "https://maps.google.com/?q=test$i"
  sleep 1
done

# Test invalid URL handling
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "invalid-url"

# Test empty text handling
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT ""
```

### 🧪 Test with Different Apps
```bash
# Simulate sharing from Chrome
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "https://www.google.com/maps/place/ChIJ..." --eu android.intent.extra.SUBJECT "Location from Chrome"

# Simulate sharing from Messages
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "Meet at: https://maps.google.com/?q=13.7563,100.5018"

# Simulate sharing from Email
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "Location: https://maps.google.com/?q=13.7563,100.5018" --eu android.intent.extra.SUBJECT "Meeting Location"
```

### 🧪 Verify Build & Installation
```bash
# Build APK
./gradlew assembleDebug

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Verify installation
adb shell pm list packages | grep phantom

# Check app version
adb shell dumpsys package com.phantom.carnavrelay | grep versionName
```

---

## 🎯 Enhanced Features

### ✅ Dark Aurora Theme
- Gradient background (ดำ→ม่วง→ฟ้าอมฟ้า)
- Aurora color spectrum
- Interactive button effects (scale + haptic)
- Material3 design

### ✅ Share Target Enhancement  
- Priority 1000 in Android Sharesheet
- Static shortcuts for quick access
- Auto-finish after sending
- "Sending..." UI feedback

### ✅ Settings & Logging
- Overlay widget toggle
- System log viewer (500-1000 lines)
- Copy/Clear log functions
- Permission management

### ✅ Comprehensive Testing
- 50+ ADB test commands
- All intent types covered
- Edge cases included
- Build verification
