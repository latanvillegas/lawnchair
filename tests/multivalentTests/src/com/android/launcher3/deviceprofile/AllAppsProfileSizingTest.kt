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
    fun portraitReducesColumnsWhen250PercentIconsWouldOtherwiseShrink() {
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 6,
                availableWidthPx = 960,
                desiredIconSizePx = 220,
                iconSizeFactor = 2.5f,
                minSpacingPx = 12,
            ),
        ).isEqualTo(3)
    }

    @Test
    fun portraitPreservesSixColumnsWhenLargerIconsAlreadyFit() {
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 6,
                availableWidthPx = 960,
                desiredIconSizePx = 112,
                iconSizeFactor = 2.5f,
                minSpacingPx = 12,
            ),
        ).isEqualTo(6)
    }

    @Test
    fun defaultAndSmallIconSizesDoNotOverrideUserColumnCount() {
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 10,
                availableWidthPx = 360,
                desiredIconSizePx = 70,
                iconSizeFactor = 1f,
                minSpacingPx = 8,
            ),
        ).isEqualTo(10)
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 10,
                availableWidthPx = 360,
                desiredIconSizePx = 35,
                iconSizeFactor = 0.5f,
                minSpacingPx = 8,
            ),
        ).isEqualTo(10)
    }

    @Test
    fun portraitVeryNarrowWindowNeverProducesZeroColumns() {
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 6,
                availableWidthPx = 280,
                desiredIconSizePx = 800,
                iconSizeFactor = 2.5f,
                minSpacingPx = 8,
            ),
        ).isEqualTo(1)
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 6,
                availableWidthPx = -1,
                desiredIconSizePx = 180,
                iconSizeFactor = 2.5f,
                minSpacingPx = 8,
            ),
        ).isEqualTo(6)
    }

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
    fun portraitTabletCanUseGutterFor250PercentIcons() {
        // Portrait: six columns on a narrower screen. The logical cell may be
        // only 92 px, but a 64 px gutter gives enough grid-span space for 120 px.
        assertThat(
            AllAppsProfile.fitResponsiveIconSizePx(
                baseSizePx = 36,
                factor = 2.5f,
                columnWidthPx = 92,
                columnGutterPx = 64,
                preferredSizePx = 120,
            ),
        ).isEqualTo(120)
    }

    @Test
    fun wideTabletKeepsUserSelectedSizeWithoutUnwantedUpscaling() {
        assertThat(
            AllAppsProfile.fitResponsiveIconSizePx(
                baseSizePx = 48,
                factor = 2.5f,
                columnWidthPx = 192,
                columnGutterPx = 64,
                preferredSizePx = 120,
            ),
        ).isEqualTo(120)
    }

    @Test
    fun narrowGridRespectsTouchTargetsAndCannotOverlapAdjacentIcons() {
        assertThat(
            AllAppsProfile.fitResponsiveIconSizePx(
                baseSizePx = 60,
                factor = 2.5f,
                columnWidthPx = 70,
                columnGutterPx = 20,
                preferredSizePx = 150,
            ),
        ).isEqualTo(81)
    }

    @Test
    fun smallUserIconsStaySmallEvenWithExtraGutterSpace() {
        assertThat(
            AllAppsProfile.fitResponsiveIconSizePx(
                baseSizePx = 48,
                factor = 0.5f,
                columnWidthPx = 92,
                columnGutterPx = 64,
                preferredSizePx = 24,
            ),
        ).isEqualTo(24)
    }

    @Test
    fun responsiveRowHeightFollowsConfiguredSliderWithoutClipping() {
        assertThat(
            AllAppsProfile.calculateResponsiveRowHeightPx(
                specHeightPx = 160,
                iconSizePx = 40,
                drawablePaddingPx = 4,
                textHeightPx = 10,
                maxTextLines = 1,
                gutterPx = 8,
                rowHeightFactor = 1.5f,
            ),
        ).isEqualTo(248)
        assertThat(
            AllAppsProfile.calculateResponsiveRowHeightPx(
                specHeightPx = 160,
                iconSizePx = 80,
                drawablePaddingPx = 4,
                textHeightPx = 16,
                maxTextLines = 1,
                gutterPx = 8,
                rowHeightFactor = 0.3f,
            ),
        ).isEqualTo(140)
    }

    @Test
    fun corruptRowHeightFactorUsesDefaultSafely() {
        assertThat(
            AllAppsProfile.calculateResponsiveRowHeightPx(
                specHeightPx = 160,
                iconSizePx = 40,
                drawablePaddingPx = 4,
                textHeightPx = 10,
                maxTextLines = 1,
                gutterPx = 8,
                rowHeightFactor = Float.NaN,
            ),
        ).isEqualTo(168)
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
