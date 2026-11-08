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
    fun tabletPortraitScalableGridHonors250PercentWithoutSmallCellCap() {
        // The legacy grid XML has a 96dp portrait cell and 60dp base icon.
        // The bitmap at 250% is 150dp; available space is sufficient.
        val result = AllAppsProfile.fitScalablePortraitIcon(
            requestedIconSizePx = 150,
            baseCellWidthPx = 96,
            defaultBorderPx = 8,
            availableWidthPx = 960,
            visibleColumns = 6,
            edgeInsetPx = 8,
        )
        assertThat(result.iconSizePx).isEqualTo(150)
        assertThat(result.cellWidthPx).isEqualTo(150)
        assertThat(result.borderSpacePx).isEqualTo(8)
    }

    @Test
    fun portraitIconGrowsPast150PercentWithSameStoredColumns() {
        val normal = AllAppsProfile.fitScalablePortraitIcon(
            requestedIconSizePx = 90,
            baseCellWidthPx = 96,
            defaultBorderPx = 8,
            availableWidthPx = 960,
            visibleColumns = 6,
            edgeInsetPx = 8,
        )
        val large = AllAppsProfile.fitScalablePortraitIcon(
            requestedIconSizePx = 150,
            baseCellWidthPx = 96,
            defaultBorderPx = 8,
            availableWidthPx = 960,
            visibleColumns = 6,
            edgeInsetPx = 8,
        )
        assertThat(normal.iconSizePx).isEqualTo(90)
        assertThat(large.iconSizePx).isEqualTo(150)
    }

    @Test
    fun portraitCanUseFewerVisibleColumnsWithoutChangingSavedPreference() {
        val effectiveColumns = AllAppsProfile.fitPortraitDrawerColumns(
            configuredColumns = 6,
            availableWidthPx = 960,
            desiredIconSizePx = 225,
            iconSizeFactor = 2.5f,
            minSpacingPx = 8,
        )
        assertThat(effectiveColumns).isEqualTo(3)
        val fit = AllAppsProfile.fitScalablePortraitIcon(
            requestedIconSizePx = 225,
            baseCellWidthPx = 96,
            defaultBorderPx = 8,
            availableWidthPx = 960,
            visibleColumns = effectiveColumns,
            edgeInsetPx = 8,
        )
        assertThat(fit.iconSizePx).isEqualTo(225)
    }

    @Test
    fun defaultAndSmallIconsKeepConfiguredColumnCount() {
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 6,
                availableWidthPx = 960,
                desiredIconSizePx = 60,
                iconSizeFactor = 1f,
                minSpacingPx = 8,
            ),
        ).isEqualTo(6)
        assertThat(
            AllAppsProfile.fitPortraitDrawerColumns(
                configuredColumns = 6,
                availableWidthPx = 960,
                desiredIconSizePx = 30,
                iconSizeFactor = 0.5f,
                minSpacingPx = 8,
            ),
        ).isEqualTo(6)
    }

    @Test
    fun portraitAtMaximumColumnsCapsOnlyAtPhysicalScreenWidth() {
        val result = AllAppsProfile.fitScalablePortraitIcon(
            requestedIconSizePx = 150,
            baseCellWidthPx = 96,
            defaultBorderPx = 8,
            availableWidthPx = 900,
            visibleColumns = 6,
            edgeInsetPx = 8,
        )
        assertThat(result.iconSizePx).isEqualTo(147)
        assertThat(result.borderSpacePx).isEqualTo(0)
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
