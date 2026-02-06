package com.phantom.carnavrelay

import android.content.Context
import android.content.Intent
import kotlin.random.Random

object DisplayModeFlow {
  fun start(ctx: Context) {
    val code = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
    val i = Intent(ctx, DisplayActivity::class.java).apply {
      putExtra("code", code)
    }
    ctx.startActivity(i)
  }
}
