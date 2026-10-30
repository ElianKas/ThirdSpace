package com.example.thirdspace

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/**
 * Minimal placeholder Activity.
 *
 * The Android app is intentionally non-functional during the iOS-native
 * refactor: Compose UI has been removed from the shared module, so this
 * Activity only needs to compile and launch. It shows a trivial programmatic
 * view and does NOT depend on Compose or AppCompat. A real Android UI returns
 * in a later phase.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            text = "ThirdSpace (Android placeholder)"
            gravity = Gravity.CENTER
        }
        setContentView(view)
    }
}
