package com.rasel.RasFocus.selfcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rasel.RasFocus.selfcontrol.study_tools.ProfessionalDiaryScreen
import com.rasel.RasFocus.selfcontrol.study_tools.StudyToolsScreen

/**
 * StudyToolsActivity — SelfControlModule এর StudyToolsCard এই Activity launch করে।
 * StudyToolsScreen composable (study_tools.kt) কে host করে।
 *
 * StudyToolsScreen এর ভেতরের "Personal Diary" কার্ডে ট্যাপ করলে onOpenDiary()
 * ট্রিগার হয়। সেই অনুযায়ী এই Activity নিজের মধ্যেই StudyToolsScreen আর
 * ProfessionalDiaryScreen (diary.kt) এর মধ্যে টগল করে — আলাদা কোনো Activity
 * লাগে না।
 */
class StudyToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var showDiary by remember { mutableStateOf(false) }

                if (showDiary) {
                    ProfessionalDiaryScreen(
                        onNavigateBack = { showDiary = false }
                    )
                } else {
                    StudyToolsScreen(
                        onBack = { finish() },
                        onOpenDiary = { showDiary = true }
                    )
                }
            }
        }
    }
}