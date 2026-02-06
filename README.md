# CarNav Relay BT

แอปเดียวติดตั้งได้สองเครื่อง:
- **Display (จอติดรถ)**: โชว์โค้ด 6 หลัก + เปิด server รอรับคำสั่ง OPEN_URL
- **Main (เครื่องหลัก)**: เลือก paired device + ใส่โค้ด 6 หลัก + ดัก intent/geo แล้วส่ง URL ไปจอรถ

## วิธีทดสอบ
1) ไปจับคู่ Bluetooth ระหว่าง 2 เครื่องใน Settings ก่อน
2) จอรถเปิดแอป → เลือก Display → เห็นรหัส 6 หลัก → เปิดค้างไว้
3) เครื่องหลักเปิดแอป → เลือก Main → เลือก device จาก paired → ใส่ code → เชื่อมต่อ
4) จากแอปอื่นกดนำทาง/เปิดลิงก์แผนที่ แล้วเลือก **CarNav Relay BT** เป็นตัวเปิด (Always)
5) ตรวจว่าจอรถเปิด Google Maps ได้อัตโนมัติ

## GitHub Actions
Repo นี้มี workflow build debug APK และแนบ artifact ให้ใน Actions
