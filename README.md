# PHANToM GO

ส่งลิงก์แผนที่/ตำแหน่งจาก **เครื่องหลัก (Main)** ไปยัง **เครื่องจอในรถ (Display)** ผ่าน Wi‑Fi ในเครือข่ายเดียวกัน (HTTP Server) พร้อมระบบจับคู่ด้วย QR และโหมดจัดการลิงก์แผนที่แบบ “Map Link Hub”.

## ภาพรวมการทำงาน
- **Display** เปิด HTTP Server บนพอร์ต `8765` และสร้าง QR (มี `ip/port/token`)
- **Main** สแกน QR เพื่อจับคู่ จากนั้นส่งลิงก์ไปที่ `http://<ip>:8765/open-url`
- Display เปิด Google Maps ตามการตั้งค่าที่เลือก (ดูแผนที่/เริ่มนำทาง)

> ต้องอยู่ใน Wi‑Fi/Hotspot เดียวกันเพื่อส่งข้อมูลได้

---

## วิธีใช้งาน (เริ่มต้น)

### 1) เครื่องจอ (Display)
1. เปิดแอป → เข้า **Display Mode**
2. เปิดบริการ (Foreground Service) และค้างหน้าจอไว้
3. แสดง QR สำหรับจับคู่

### 2) เครื่องหลัก (Main)
1. เปิดแอป → **Main Mode**
2. สแกน QR จากเครื่องจอ
3. สถานะขึ้นว่า **Paired / Connected**

### 3) ส่งลิงก์แผนที่
1. จาก Google Maps / Chrome / LINE / ฯลฯ กด **Share**
2. เลือก **PHANToM GO**
3. ระบบจะ Normalize/Resolve ลิงก์ แล้วส่งไปเครื่องจอ

---

## Map Link Hub (รับลิงก์แทน Google Maps)

ใน **Settings**:
- **Map Link Hub**: เปิด/ปิดการเป็นตัวรับลิงก์แผนที่ (VIEW intents)
- **Smart Mode**: เลือกนโยบาย
  - `AUTO_SEND_NAV_ONLY` ส่งเฉพาะลิงก์นำทาง
  - `ALWAYS_ASK` ถามทุกครั้ง
  - `ALWAYS_SEND` ส่งทุกลิงก์
  - `ALWAYS_OPEN_ON_PHONE` เปิด Maps บนมือถือหลักทุกครั้ง

> ถ้าเปิด Map Link Hub ให้ตั้งค่า “Open by default” ในระบบ:
> Settings ระบบ → Apps → PHANToM GO → Open by default → Always

---

## การตั้งค่าเครื่องจอ (Display)

ใน Display Mode:
- **Navigation Mode**: รถยนต์ / มอเตอร์ไซค์
- **Open Behavior**:
  - `PREVIEW_ROUTE` เปิดเส้นทางแบบดูแผนที่
  - `START_NAVIGATION` พยายามเริ่มนำทางทันที
- **Overlay Widget**: เปิด/ปิดวิดเจ็ตลอย
- **Battery Optimization**: แนะนำปิดเพื่อความเสถียร

---

## การเชื่อมต่ออัตโนมัติ (Auto Reconnect)
- ถ้า Wi‑Fi เปลี่ยน IP, Main จะพยายามค้นหา Display ใหม่ใน subnet เดียวกัน
- จะอัปเดต IP ให้เองหากพบ token ที่ตรงกัน
- หากมีการ **Refresh token** ที่จอ ต้องสแกน QR ใหม่

---

## Permissions ที่ใช้
- Camera (สแกน QR)
- Internet / Network State
- Notifications (เลือกเปิดใน Settings เท่านั้น)
- Overlay (ตัวเลือก)

---

## Build
```bash
./gradlew clean assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## ADB Test (สำคัญ)

### ✅ Share Sheet (ACTION_SEND)
```bash
adb shell cmd package query-intent-activities -a android.intent.action.SEND -t text/plain | grep -i phantom -n
```

### ✅ Map Link Handler (ACTION_VIEW)
```bash
adb shell cmd package query-intent-activities -a android.intent.action.VIEW -d "geo:13.7563,100.5018" | grep -i phantom -n
adb shell cmd package query-intent-activities -a android.intent.action.VIEW -d "https://www.google.com/maps?q=13.7563,100.5018" | grep -i phantom -n
```

### ✅ ส่งลิงก์แบบทดสอบ
```bash
adb shell am start -a android.intent.action.SEND -t "text/plain" --es android.intent.extra.TEXT "https://www.google.com/maps?q=13.7563,100.5018"
```

---

## หมายเหตุสำคัญ
- หากต้องการให้ PHANToM GO รับลิงก์แทน Maps ต้องเปิด Map Link Hub และตั้งค่า Always ในระบบ
- หาก Maps ไม่เปิดอัตโนมัติ ให้ตรวจการตั้งค่า Display Open Behavior
- หากแอปไม่แสดงใน Share Sheet ให้ตรวจว่า ShareReceiverActivity ยังถูก enabled
