package com.rasel.RasFocus.selfcontrol.study_tools

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class PptxViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "PPTX Viewer is not available in the Light build", Toast.LENGTH_SHORT).show()
        finish()
    }
}
