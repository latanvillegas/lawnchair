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
    fun portraitTabletIconScalesFrom150To250PercentInsteadOfStoppingAtXmlCell() {
        // 6x5 tablet XML: 60dp icon and 96dp portrait cell at mdpi.
        // The 250% bitmap must be 150px, not capped to 90/96px.
        val at150 = AllAppsProfile.fitPortraitScalableIcon(
            requestedSizePx = 90,
            baseCellWidthPx = 96,
            availableWidthPx = 960,
            visibleColumns = 6,
            edgeInsetPx = 8,
        )
        val at250 = AllAppsProfile.fitPortraitScalableIcon(
            requestedSizePx = 150,
            baseCellWidthPx = 96,
            availableWidthPx = 960,
            visibleColumns = 6,
            edgeInsetPx = 8,
        )
        assertThat(at150).isEqualTo(90)
        assertThat(at250).isEqualTo(150)
        assertThat(at250).isGreaterThan(at150)
    }

    @Test
    fun portrait250PercentKeepsSixColumnsWhenSixActualIconsFit() {
        assertThat(
            AllAppsProfile.portraitColumnsForIcon(
                configuredColumns = 6,
                availableWidthPx = 960,
                iconSizePx = 150,
                scaleFactor = 2.5f,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(6)
    }

    @Test
    fun portrait250PercentReducesOnlyEffectiveColumnsWhenRequired() {
        val actualColumns = AllAppsProfile.portraitColumnsForIcon(
            configuredColumns = 6,
            availableWidthPx = 960,
            iconSizePx = 225,
            scaleFactor = 2.5f,
            edgeInsetPx = 8,
        )
        assertThat(actualColumns).isEqualTo(4)
        assertThat(
            AllAppsProfile.fitPortraitScalableIcon(
                requestedSizePx = 225,
                baseCellWidthPx = 96,
                availableWidthPx = 960,
                visibleColumns = actualColumns,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(225)
    }

    @Test
    fun portraitRespectsPhysicalWidthWithoutOverlappingIcons() {
        assertThat(
            AllAppsProfile.portraitColumnWidthBudget(
                availableWidthPx = 360,
                visibleColumns = 4,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(86)
        assertThat(
            AllAppsProfile.fitPortraitScalableIcon(
                requestedSizePx = 250,
                baseCellWidthPx = 96,
                availableWidthPx = 360,
                visibleColumns = 4,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(86)
    }

    @Test
    fun normalAndSmallIconScalePreservesManuallyConfiguredGrid() {
        assertThat(
            AllAppsProfile.portraitColumnsForIcon(
                configuredColumns = 6,
                availableWidthPx = 360,
                iconSizePx = 90,
                scaleFactor = 1f,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(6)
        assertThat(
            AllAppsProfile.portraitColumnsForIcon(
                configuredColumns = 6,
                availableWidthPx = 360,
                iconSizePx = 45,
                scaleFactor = 0.5f,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(6)
    }

    @Test
    fun invalidPortraitWindowIsBoundedAndNeverProducesZeroColumns() {
        assertThat(
            AllAppsProfile.portraitColumnsForIcon(
                configuredColumns = 6,
                availableWidthPx = -1,
                iconSizePx = 150,
                scaleFactor = 2.5f,
                edgeInsetPx = 8,
            ),
        ).isEqualTo(6)
        assertThat(
            AllAppsProfile.portraitColumnsForIcon(
                configuredColumns = 6,
                availableWidthPx = 360,
                iconSizePx = 150,
                scaleFactor = Float.NaN,
                edgeInsetPx = 8,
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
    fun invalidScaleCannotAllocateUnboundedIcons() {
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, Float.NaN, 180)).isEqualTo(56)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, Float.POSITIVE_INFINITY, 180))
            .isEqualTo(56)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, 100f, 180)).isEqualTo(140)
        assertThat(AllAppsProfile.fitResponsiveIconSizePx(56, -10f, 180)).isEqualTo(28)
    }
}
