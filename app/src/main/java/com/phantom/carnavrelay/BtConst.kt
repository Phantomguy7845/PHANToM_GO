package com.phantom.carnavrelay

import java.util.UUID

object BtConst {
  // UUID เดียวกันทุกเครื่อง
  val APP_UUID: UUID = UUID.fromString("2f6c4d6a-9f6e-4b7e-8d0e-3a4a9d8d1f01")
  const val SERVICE_NAME = "CarNavRelayBT"

  // Broadcast ภายในจอรถ
  const val ACTION_OPEN_URL = "com.phantom.carnavrelay.OPEN_URL"
  const val EXTRA_URL = "url"
}
