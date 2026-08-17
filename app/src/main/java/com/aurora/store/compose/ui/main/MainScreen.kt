/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.extensions.requiresObbDir
import com.aurora.store.R
import com.aurora.store.compose.composable.TopAppBar
import com.aurora.store.compose.composable.TrackerUpdateWarningDialog
import com.aurora.store.compose.composition.LocalNetworkStatus
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.compose.ui.commons.MoreSheet
import com.aurora.store.compose.ui.commons.NetworkScreen
import com.aurora.store.compose.ui.sheets.AppUpdateSheet
import com.aurora.store.compose.ui.updates.UpdatesScreen
import com.aurora.store.data.model.ExodusTracker
import com.aurora.store.data.model.NetworkStatus
import com.aurora.store.data.model.PermissionType
import com.aurora.store.data.providers.PermissionProvider.Companion.isGranted
import com.aurora.store.data.room.update.Update
import com.aurora.store.util.PackageUtil
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_UPDATES_WARN_TRACKERS
import com.aurora.store.viewmodel.all.UpdatesViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Update-only home screen. The legacy tab index is intentionally accepted so existing saved
 * navigation state and account/onboarding routes remain compatible with the upstream app.
 */
@Composable
fun MainScreen(
    @Suppress("UNUSED_PARAMETER") initialTab: Int = 0,
    updatesViewModel: UpdatesViewModel = hiltViewModel(),
    onNavigateTo: (Destination) -> Unit = {}
) {
    val context = LocalContext.current
    val networkStatus = LocalNetworkStatus.current
    val downloads by updatesViewModel.downloadsList.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    var showMoreSheet by remember { mutableStateOf(false) }
    var appUpdateTarget by remember { mutableStateOf<Update?>(null) }
    var trackerWarning by remember {
        mutableStateOf<Pair<Update, List<ExodusTracker>>?>(null)
    }
    val checkingJobs = remember { mutableStateMapOf<String, Job>() }

    // Once the download a check kicked off actually appears, drop the "checking" marker so the
    // item's in-progress state is driven purely by the download (no flash back to "Update").
    LaunchedEffect(downloads) {
        checkingJobs.keys.toList().forEach { packageName ->
            if (downloads.any { it.packageName == packageName && !it.isFinished }) {
                checkingJobs.remove(packageName)
            }
        }
    }

    fun handleNavigation(destination: Destination) {
        when (destination) {
            is Destination.AppUpdate -> appUpdateTarget = destination.update
            else -> onNavigateTo(destination)
        }
    }

    fun performUpdate(update: Update) {
        if (update.fileList.requiresObbDir() &&
            !isGranted(context, PermissionType.STORAGE_MANAGER)
        ) {
            checkingJobs.remove(update.packageName)
            onNavigateTo(
                Destination.PermissionRationale(setOf(PermissionType.STORAGE_MANAGER))
            )
        } else {
            updatesViewModel.download(update)
        }
    }

    if (networkStatus == NetworkStatus.UNAVAILABLE) {
        NetworkScreen()
        return
    }

    if (showMoreSheet) {
        MoreSheet(
            onDismiss = { showMoreSheet = false },
            onNavigateTo = { destination ->
                showMoreSheet = false
                onNavigateTo(destination)
            }
        )
    }

    appUpdateTarget?.let { update ->
        AppUpdateSheet(
            update = update,
            onDismiss = { appUpdateTarget = null },
            onNavigateTo = { destination ->
                appUpdateTarget = null
                onNavigateTo(destination)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_updates),
                showNavigationIcon = false,
                actions = {
                    IconButton(onClick = { onNavigateTo(Destination.Downloads) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download_manager),
                            contentDescription = stringResource(R.string.title_download_manager)
                        )
                    }
                    IconButton(onClick = { showMoreSheet = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_account),
                            contentDescription = stringResource(R.string.title_more)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .fillMaxSize()
        ) {
            UpdatesScreen(
                viewModel = updatesViewModel,
                onNavigateTo = ::handleNavigation,
                onRequestUpdate = { update ->
                    if (!Preferences.getBoolean(
                            context,
                            PREFERENCE_UPDATES_WARN_TRACKERS,
                            false
                        )
                    ) {
                        performUpdate(update)
                    } else {
                        val job = coroutineScope.launch {
                            val installedVersionCode = PackageUtil.getInstalledVersionCode(
                                context,
                                update.packageName
                            )
                            val trackers = updatesViewModel.getNewTrackers(
                                update.packageName,
                                installedVersionCode
                            )
                            if (trackers.isEmpty()) {
                                performUpdate(update)
                            } else {
                                trackerWarning = update to trackers
                            }
                        }
                        checkingJobs[update.packageName] = job
                    }
                },
                onRequestUpdateAll = { selectedUpdates ->
                    val needsObb = selectedUpdates.any { it.fileList.requiresObbDir() }
                    if (needsObb && !isGranted(context, PermissionType.STORAGE_MANAGER)) {
                        onNavigateTo(
                            Destination.PermissionRationale(
                                setOf(PermissionType.STORAGE_MANAGER)
                            )
                        )
                    } else {
                        updatesViewModel.downloadAll(selectedUpdates)
                    }
                },
                onCancelUpdate = { packageName ->
                    if (downloads.any {
                            it.packageName == packageName && !it.isFinished
                        }
                    ) {
                        checkingJobs.remove(packageName)
                        updatesViewModel.cancelDownload(packageName)
                    } else {
                        checkingJobs.remove(packageName)?.cancel()
                    }
                },
                onCancelAll = { updatesViewModel.cancelAll() },
                checkingPackages = checkingJobs.keys
            )
        }
    }

    trackerWarning?.let { (update, trackers) ->
        TrackerUpdateWarningDialog(
            trackers = trackers,
            onConfirm = {
                trackerWarning = null
                performUpdate(update)
            },
            onDismiss = {
                trackerWarning = null
                checkingJobs.remove(update.packageName)
            }
        )
    }
}
