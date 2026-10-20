package com.rotatsulabs.specty.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Saving to Pictures needs a storage permission before Android 10 and none after. Returns a function that runs
 * [action] once saving is allowed, asking first where it is needed.
 */
@Composable
fun rememberStorageGate(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val current by rememberUpdatedState(action)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) current() else Toast.makeText(context, context.getString(com.rotatsulabs.specty.R.string.toast_storage_permission), Toast.LENGTH_SHORT).show()
    }
    return {
        val needed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needed) launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else current()
    }
}
