/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import android.content.Context
import com.aurora.store.data.model.BuildType

/**
 * Product-level restrictions for this update-only Aurora Store fork.
 *
 * A package may be viewed when it is already installed, but APK acquisition and installation are
 * limited to a strictly newer version of an installed package. Nightly self-updates are the only
 * exception: upstream nightlies intentionally reuse a static version code, so their feed performs
 * the freshness check before enqueueing the APK.
 */
object UpdateOnlyPolicy {

    fun canViewApp(context: Context, packageName: String): Boolean =
        PackageUtil.isInstalled(context, packageName)

    fun canAcquireUpgrade(context: Context, packageName: String, versionCode: Long): Boolean {
        if (!canViewApp(context, packageName)) return false

        val installedVersionCode = PackageUtil.getInstalledVersionCode(context, packageName)
        val allowEqualVersion = packageName == context.packageName &&
            BuildType.CURRENT == BuildType.NIGHTLY
        return isVersionUpgradeAllowed(installedVersionCode, versionCode, allowEqualVersion)
    }

    fun requireUpgrade(context: Context, packageName: String, versionCode: Long) {
        if (!canAcquireUpgrade(context, packageName, versionCode)) {
            throw SecurityException(
                "Update-only mode rejected $packageName version $versionCode"
            )
        }
    }

    internal fun isVersionUpgradeAllowed(
        installedVersionCode: Long,
        requestedVersionCode: Long,
        allowEqualVersion: Boolean = false
    ): Boolean = if (allowEqualVersion) {
        requestedVersionCode >= installedVersionCode
    } else {
        requestedVersionCode > installedVersionCode
    }
}
