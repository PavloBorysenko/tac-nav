package com.tacnav.beacon

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Debug-only host, so screen tests also run on a phone left locked. */
class ComposeTestHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        super.onCreate(savedInstanceState)
    }
}
