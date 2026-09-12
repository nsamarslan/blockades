package com.emre.bilbakalim.arsiv.capture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Ekran yakalama iznini isteyen görünmez ara ekran.
 * Sadece Android 10 ve altında ya da erişilebilirlik ekran görüntüsü
 * çalışmadığında kullanılır.
 */
class ProjectionPermissionActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val svc = Intent(this, ProjectionService::class.java)
                .putExtra(ProjectionService.EXTRA_CODE, result.resultCode)
                .putExtra(ProjectionService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(this, svc)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MediaProjectionManager::class.java)
        runCatching { launcher.launch(mgr.createScreenCaptureIntent()) }
            .onFailure { finish() }
    }
}
