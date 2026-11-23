package com.example.fridatestbench.components

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class ExportedTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Exported activity test fixture"
            setPadding(32, 32, 32, 32)
        })
    }
}
