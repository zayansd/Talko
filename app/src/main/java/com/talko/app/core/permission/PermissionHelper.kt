package com.talko.app.core.permission

import android.Manifest
import androidx.compose.runtime.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Remembers and requests RECORD_AUDIO + CAMERA permissions.
 * Returns true once all required permissions are granted.
 *
 * Usage:
 *   val granted = rememberCallPermissions(needCamera = callType == VIDEO)
 *   if (!granted) { /* show rationale */ }
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberCallPermissions(needCamera: Boolean): Boolean {
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (needCamera) add(Manifest.permission.CAMERA)
    }
    val state = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(Unit) {
        if (!state.allPermissionsGranted) {
            state.launchMultiplePermissionRequest()
        }
    }

    return state.allPermissionsGranted
}
