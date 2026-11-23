package com.example.fridatestbench.components

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log

class ExportedTestService : Service() {
    private val binder = Binder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("ExportedTestService", "Exported service test fixture started")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
