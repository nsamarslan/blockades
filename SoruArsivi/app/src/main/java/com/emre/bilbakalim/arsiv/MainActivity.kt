package com.emre.bilbakalim.arsiv

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.emre.bilbakalim.arsiv.ui.AppPickerScreen
import com.emre.bilbakalim.arsiv.ui.ArsivViewModel
import com.emre.bilbakalim.arsiv.ui.DebugScreen
import com.emre.bilbakalim.arsiv.ui.DetailScreen
import com.emre.bilbakalim.arsiv.ui.HomeScreen
import com.emre.bilbakalim.arsiv.ui.ListScreen
import com.emre.bilbakalim.arsiv.ui.MissesScreen
import com.emre.bilbakalim.arsiv.ui.SettingsScreen
import com.emre.bilbakalim.arsiv.ui.SoruArsiviTheme

sealed interface Screen {
    data object Home : Screen
    data object Liste : Screen
    data object Ayarlar : Screen
    data object Teshis : Screen
    data object Hatalar : Screen
    data object UygulamaSec : Screen
    data class Detay(val id: Long) : Screen
}

class MainActivity : ComponentActivity() {

    private val vm: ArsivViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoruArsiviTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: ArsivViewModel) {
    val context = LocalContext.current
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = backStack.last()

    fun go(s: Screen) { backStack.add(s) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { back() }

    // Android 13+ bildirim izni — sessiz sayaç bildirimi için.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val settings by vm.settings.collectAsState()

    AnimatedContent(
        targetState = current,
        transitionSpec = { fadeIn(tween()) togetherWith fadeOut(tween()) },
        label = "ekran"
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(
                vm = vm,
                onOpenList = { go(Screen.Liste) },
                onOpenSettings = { go(Screen.Ayarlar) },
                onOpenDebug = { go(Screen.Teshis) },
                onOpenMisses = { go(Screen.Hatalar) },
                onPickApp = { go(Screen.UygulamaSec) },
                onOpenDetail = { go(Screen.Detay(it)) }
            )
            Screen.Liste -> ListScreen(
                vm = vm,
                onBack = { back() },
                onOpenDetail = { go(Screen.Detay(it)) }
            )
            Screen.Ayarlar -> SettingsScreen(
                vm = vm,
                onBack = { back() },
                onPickApp = { go(Screen.UygulamaSec) },
                onOpenDebug = { go(Screen.Teshis) }
            )
            Screen.Teshis -> DebugScreen(vm = vm, onBack = { back() })
            Screen.Hatalar -> MissesScreen(
                vm = vm,
                onBack = { back() },
                onOpenDetail = { go(Screen.Detay(it)) }
            )
            Screen.UygulamaSec -> AppPickerScreen(
                vm = vm,
                selected = settings.targetPackages,
                onBack = { back() }
            )
            is Screen.Detay -> DetailScreen(vm = vm, id = screen.id, onBack = { back() })
        }
    }
}

private fun tween() = androidx.compose.animation.core.tween<Float>(180)
