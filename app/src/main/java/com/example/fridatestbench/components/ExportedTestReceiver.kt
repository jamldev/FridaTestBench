package com.example.fridatestbench.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ExportedTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("ExportedTestReceiver", "Exported receiver test fixture invoked")
    }
}
