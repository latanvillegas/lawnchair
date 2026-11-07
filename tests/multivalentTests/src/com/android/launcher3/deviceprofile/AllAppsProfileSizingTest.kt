/*
 * Copyright (C) 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package com.android.launcher3.deviceprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AllAppsProfileSizingTest {

    @Test
    fun portraitKeepsRequestedLargeIconWhenColumnHasSpace() {
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, 2f, 144)).isEqualTo(112)
    }

    @Test
    fun landscapeAndPortraitUseTheSameColumnWidthBounds() {
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, 2.5f, 92)).isEqualTo(92)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, 2.5f, 160)).isEqualTo(140)
    }

    @Test
    fun portraitRowGrowsToHoldIconAndLabelsWithoutCropping() {
        assertThat(
            AllAppsProfile.calculateResponsiveRowHeightPx(
                specHeightPx = 96,
                iconSizePx = 112,
                drawablePaddingPx = 4,
                textHeightPx = 16,
                maxTextLines = 1,
                gutterPx = 8,
            ),
        ).isEqualTo(172)
    }

    @Test
    fun responsiveRowRetainsMinimumSpecHeightAndNonnegativeGutter() {
        assertThat(
            AllAppsProfile.calculateResponsiveRowHeightPx(
                specHeightPx = 160,
                iconSizePx = 52,
                drawablePaddingPx = 4,
                textHeightPx = 16,
                maxTextLines = 1,
                gutterPx = -5,
            ),
        ).isEqualTo(160)
    }

    @Test
    fun invalidScaleCannotAllocateUnboundedIcons() {
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, Float.NaN, 180)).isEqualTo(56)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, Float.POSITIVE_INFINITY, 180))
            .isEqualTo(56)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, 100f, 180)).isEqualTo(140)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, -10f, 180)).isEqualTo(28)
    }
}
