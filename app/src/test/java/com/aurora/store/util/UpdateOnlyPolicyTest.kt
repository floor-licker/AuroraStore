/*
 * SPDX-FileCopyrightText: 2026 Aurora OSS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateOnlyPolicyTest {

    @Test
    fun newerVersionIsAllowed() {
        assertThat(UpdateOnlyPolicy.isVersionUpgradeAllowed(100, 101)).isTrue()
    }

    @Test
    fun sameAndOlderVersionsAreRejected() {
        assertThat(UpdateOnlyPolicy.isVersionUpgradeAllowed(100, 100)).isFalse()
        assertThat(UpdateOnlyPolicy.isVersionUpgradeAllowed(100, 99)).isFalse()
    }

    @Test
    fun equalVersionCanBeAllowedForNightlySelfUpdate() {
        assertThat(
            UpdateOnlyPolicy.isVersionUpgradeAllowed(
                installedVersionCode = 100,
                requestedVersionCode = 100,
                allowEqualVersion = true
            )
        ).isTrue()
    }
}
