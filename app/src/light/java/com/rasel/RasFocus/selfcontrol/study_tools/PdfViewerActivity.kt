package com.rasel.RasFocus.selfcontrol.study_tools

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Light flavor stub — pdfium library নেই, তাই PDF viewer disabled।
 * শুধু ছোট size / fast build এর জন্য।
 */
class PdfViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "PDF Viewer is not available in the Light build", Toast.LENGTH_SHORT).show()
        finish()
    }
}
