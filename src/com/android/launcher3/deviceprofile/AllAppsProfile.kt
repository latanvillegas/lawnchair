/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.deviceprofile

import android.content.res.Resources
import android.graphics.Point
import android.util.DisplayMetrics
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.getIconVisibleSizePx
import com.android.launcher3.Utilities.getNormalizedIconDrawablePadding
import com.android.launcher3.Utilities.pxFromSp
import com.android.launcher3.responsive.CalculatedCellSpec
import com.android.launcher3.responsive.CalculatedResponsiveSpec
import com.android.launcher3.testing.shared.ResourceUtils.pxFromDp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AllAppsProfile(
    val borderSpacePx: Point,
    val cellHeightPx: Int,
    val iconSizePx: Int,
    val iconTextSizePx: Float,
    val iconDrawablePaddingPx: Int,
    val maxAllAppsTextLineCount: Int,
    val cellWidthPx: Int,
) {

    /**
     * Return a new AllAppsProfile with cellHeightPx changed. This function is needed for the java
     * code.
     */
    fun copyWithCellHeightPx(newCellHeightPx: Int): AllAppsProfile {
        return copy(cellHeightPx = newCellHeightPx)
    }

    companion object Factory {

        /** Portrait icon scaling uses visible columns; stored grid settings are unchanged. */
        data class PortraitIconFit(
            val iconSizePx: Int,
            val cellWidthPx: Int,
            val borderSpacePx: Int,
        )

        /**
         * Expands scalable-grid cells (96dp in the stock tablet portrait profile)
         * to fit the icon size the user selected, up to the actual viewport width.
         * Only portrait uses this path; landscape keeps its original measurements.
         */
        fun fitScalablePortraitIcon(
            requestedIconSizePx: Int,
            baseCellWidthPx: Int,
            defaultBorderPx: Int,
            availableWidthPx: Int,
            visibleColumns: Int,
            edgeInsetPx: Int,
        ): PortraitIconFit {
            val columns = visibleColumns.coerceAtLeast(1)
            val edge = edgeInsetPx.coerceAtLeast(0)
                .coerceAtMost(max(0, availableWidthPx / 8))
            val usableWidth = max(1, availableWidthPx - 2 * edge)
            val requested = requestedIconSizePx.coerceAtLeast(1)
            val baseWidth = baseCellWidthPx.coerceAtLeast(1)
            val borders = columns - 1

            // Reduce gaps before shrinking an icon, but never overlap adjacent cells.
            val remainingForGaps = usableWidth.toLong() - requested.toLong() * columns
            val border = if (borders > 0) {
                min(defaultBorderPx.coerceAtLeast(0),
                    max(0L, remainingForGaps / borders).toInt())
            } else {
                0
            }
            val width = max(1, ((usableWidth.toLong() - border.toLong() * borders) /
                columns).toInt())
            val cell = min(max(baseWidth, requested), width)
            return PortraitIconFit(min(requested, cell), cell, border)
        }

        /**
         * 250% icons cannot physically fit in the user's normal six-column tablet
         * grid at all window widths. Reduce only the effective portrait column count
         * when necessary, without changing the persisted column preference.
         */
        fun fitPortraitDrawerColumns(
            configuredColumns: Int,
            availableWidthPx: Int,
            desiredIconSizePx: Int,
            iconSizeFactor: Float,
            minSpacingPx: Int,
        ): Int {
            val columns = configuredColumns.coerceAtLeast(1)
            if (!iconSizeFactor.isFinite() || iconSizeFactor <= 1f ||
                availableWidthPx <= 0 || desiredIconSizePx <= 0
            ) return columns

            val usableWidth = max(1L, availableWidthPx.toLong() * 94L / 100L)
            val pitch = desiredIconSizePx.toLong() +
                max(minSpacingPx.coerceAtLeast(0), desiredIconSizePx / 12).toLong()
            return min(columns, max(1L, usableWidth / max(1L, pitch)).toInt())
        }


        /** Responsive drawer rows scroll vertically; grow their height instead of shrinking icons. */
        fun calculateResponsiveRowHeightPx(
            specHeightPx: Int,
            iconSizePx: Int,
            drawablePaddingPx: Int,
            textHeightPx: Int,
            maxTextLines: Int,
            gutterPx: Int,
        ): Int {
            val requiredHeight = iconSizePx + drawablePaddingPx +
                textHeightPx * (max(0, maxTextLines) + 2)
            return max(specHeightPx, requiredHeight) + max(0, gutterPx)
        }

        /** Respect the requested scale up to the measured width of one drawer column. */
        fun fitResponsiveIconSizePx(baseSizePx: Int, factor: Float, columnWidthPx: Int): Int {
            val safeFactor = if (factor.isFinite()) factor.coerceIn(0.5f, 2.5f) else 1f
            return min(max(1, columnWidthPx),
                max(1, (baseSizePx * safeFactor).roundToInt()))
        }

        private fun calculateAllAppsBorderSpacePx(
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            typeIndex: Int,
            scale: Float,
        ) =
            Point(
                pxFromDp(inv.allAppsBorderSpaces[typeIndex].x, metric, scale),
                pxFromDp(inv.allAppsBorderSpaces[typeIndex].y, metric, scale),
            )

        fun createAllAppsProfileScalableGrid(
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            typeIndex: Int,
            scale: Float,
            iconSizePx: Int,
            iconDrawablePaddingOriginalPx: Int,
            cellHeightMultiplier: Float = 1f,
            availableWidthPx: Int,
            visibleColumns: Int,
            isPortrait: Boolean,
        ): AllAppsProfile {
            val allAppsBorderSpacePx = calculateAllAppsBorderSpacePx(inv, metric, typeIndex, scale)
            var allAppsCellHeightPx =
                (pxFromDp(inv.allAppsCellSize[typeIndex].y, metric, cellHeightMultiplier) + allAppsBorderSpacePx.y)
            var allAppsIconSizePx = pxFromDp(inv.allAppsIconSize[typeIndex], metric)
            val allAppsIconTextSizePx =
                pxFromSp(inv.allAppsIconTextSize[typeIndex], metric).toFloat()
            var allAppsIconDrawablePaddingPx =
                max(
                    0,
                    (iconDrawablePaddingOriginalPx -
                        ((iconSizePx - getIconVisibleSizePx(iconSizePx)) / 2)),
                )
            var allAppsCellWidthPx = pxFromDp(inv.allAppsCellSize[typeIndex].x, metric, scale)

            if (isPortrait && allAppsIconSizePx > allAppsCellWidthPx) {
                val size = fitScalablePortraitIcon(
                    requestedIconSizePx = allAppsIconSizePx,
                    baseCellWidthPx = allAppsCellWidthPx,
                    defaultBorderPx = allAppsBorderSpacePx.x,
                    availableWidthPx = availableWidthPx,
                    visibleColumns = visibleColumns,
                    edgeInsetPx = pxFromDp(8f, metric),
                )
                allAppsIconSizePx = size.iconSizePx
                allAppsCellWidthPx = size.cellWidthPx
                allAppsBorderSpacePx.x = size.borderSpacePx
                allAppsIconDrawablePaddingPx = min(allAppsIconDrawablePaddingPx,
                    max(0, (allAppsCellWidthPx - allAppsIconSizePx) / 2))
            } else if (allAppsCellWidthPx < allAppsIconSizePx) {
                // If allAppsCellWidth no longer fit allAppsIconSize, reduce allAppsBorderSpace to
                // make allAppsCellWidth bigger.
                val numBorders: Int = inv.numAllAppsColumns - 1
                val extraWidthRequired: Int =
                    (allAppsIconSizePx - allAppsCellWidthPx) * inv.numAllAppsColumns
                if (allAppsBorderSpacePx.x * numBorders >= extraWidthRequired) {
                    allAppsCellWidthPx = allAppsIconSizePx
                    allAppsBorderSpacePx.x -= extraWidthRequired / numBorders
                } else {
                    // If it still doesn't fit, set allAppsBorderSpace to 0 and distribute the space
                    // for allAppsCellWidth, and reduce allAppsIconSize.
                    allAppsCellWidthPx =
                        (allAppsCellWidthPx * inv.numAllAppsColumns +
                            allAppsBorderSpacePx.x * numBorders) / inv.numAllAppsColumns
                    allAppsIconSizePx =
                        min(allAppsIconSizePx.toDouble(), allAppsCellWidthPx.toDouble()).toInt()
                    allAppsBorderSpacePx.x = 0
                }
            }

            val cellContentHeight: Int =
                (allAppsIconSizePx +
                    Utilities.calculateTextHeight(allAppsIconTextSizePx) +
                    allAppsBorderSpacePx.y)
            if (allAppsCellHeightPx < cellContentHeight) {
                // Increase allAppsCellHeight to fit its content.
                allAppsCellHeightPx = cellContentHeight
            }

            return AllAppsProfile(
                borderSpacePx = allAppsBorderSpacePx,
                cellHeightPx = allAppsCellHeightPx,
                iconSizePx = allAppsIconSizePx,
                iconTextSizePx = allAppsIconTextSizePx,
                iconDrawablePaddingPx = allAppsIconDrawablePaddingPx,
                maxAllAppsTextLineCount = 1,
                cellWidthPx = allAppsCellWidthPx,
            )
        }

        fun createAllAppsProfileNonScalable(
            res: Resources,
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            typeIndex: Int,
            scale: Float,
            cellHeightMultiplier: Float = 1f,
            availableWidthPx: Int,
        ): AllAppsProfile {
            val allAppsBorderSpacePx = calculateAllAppsBorderSpacePx(inv, metric, typeIndex, scale)
            // The number of columns can be set as high as ten even on narrow phones.
            // Bound icon padding as well as the bitmap so every row fits its real width.
            val columnWidthBudget = max(1, availableWidthPx / max(1, inv.numAllAppsColumns))
            val allAppsIconDrawablePaddingPx = min(
                max(0, res.getDimensionPixelSize(R.dimen.all_apps_icon_drawable_padding)),
                max(0, (columnWidthBudget - 1) / 2),
            )
            // In non-scalable grids the drawer scrolls vertically, so rows may expand.
            val iconWidthBudget = max(1, columnWidthBudget - 2 * allAppsIconDrawablePaddingPx)
            val allAppsIconSizePx = min(
                max(1, pxFromDp(inv.allAppsIconSize[typeIndex], metric, scale)),
                iconWidthBudget,
            )
            val textSizePx = pxFromSp(inv.allAppsIconTextSize[typeIndex], metric) * scale
            return AllAppsProfile(
                borderSpacePx = allAppsBorderSpacePx,
                cellHeightPx = max(
                    pxFromDp(inv.allAppsCellSize.get(typeIndex).y, metric, cellHeightMultiplier) +
                        allAppsBorderSpacePx.y,
                    allAppsIconSizePx + allAppsIconDrawablePaddingPx +
                        Utilities.calculateTextHeight(textSizePx) * 2,
                ),
                iconSizePx = allAppsIconSizePx,
                iconTextSizePx =
                    (pxFromSp(inv.allAppsIconTextSize[typeIndex], metric).toFloat() * scale)
                        .toInt()
                        .toFloat(),
                iconDrawablePaddingPx = allAppsIconDrawablePaddingPx,
                maxAllAppsTextLineCount = 1,
                cellWidthPx = allAppsIconSizePx + (2 * allAppsIconDrawablePaddingPx),
            )
        }

        fun createAllAppsWithResponsive(
            responsiveAllAppsCellSpec: CalculatedCellSpec,
            responsiveAllAppsWidthSpec: CalculatedResponsiveSpec,
            responsiveAllAppsHeightSpec: CalculatedResponsiveSpec,
            iconSizeFactor: Float,
        ): AllAppsProfile {
            // Responsive specs are independent of the IDP sizes. Apply the drawer's
            // user factor here, then resize to the measured cell width and height.
            val allAppsCellWidthPx = max(1, responsiveAllAppsWidthSpec.cellSizePx)
            // A RecyclerView can scroll vertically: its rows must grow to contain a larger
            // icon. The previous portrait-only resizeToFitCellHeight shrank every requested
            // 150–250% icon back to the fixed responsive spec height.
            val allAppsIconSizePx = fitResponsiveIconSizePx(
                responsiveAllAppsCellSpec.iconSize,
                iconSizeFactor,
                allAppsCellWidthPx,
            )
            val allAppsIconTextSizePx = responsiveAllAppsCellSpec.iconTextSize.toFloat()
            val allAppsIconDrawablePaddingPx = min(
                max(0, allAppsCellWidthPx - allAppsIconSizePx),
                getNormalizedIconDrawablePadding(
                    allAppsIconSizePx,
                    responsiveAllAppsCellSpec.iconDrawablePadding,
                ),
            )
            val maxAllAppsTextLineCount = responsiveAllAppsCellSpec.iconTextMaxLineCount
            val allAppsBorderSpacePx =
                Point(responsiveAllAppsWidthSpec.gutterPx, responsiveAllAppsHeightSpec.gutterPx)
            val textHeightPx = Utilities.calculateTextHeight(allAppsIconTextSizePx)
            // Reserve two additional label-height units for visual breathing space and
            // TextView font metrics. Preserve the responsive spec minimum where possible.
            val rowHeightPx = calculateResponsiveRowHeightPx(
                responsiveAllAppsHeightSpec.cellSizePx,
                allAppsIconSizePx,
                allAppsIconDrawablePaddingPx,
                textHeightPx,
                maxAllAppsTextLineCount,
                allAppsBorderSpacePx.y,
            )

            return AllAppsProfile(
                borderSpacePx = allAppsBorderSpacePx,
                cellHeightPx = rowHeightPx,
                iconSizePx = allAppsIconSizePx,
                iconTextSizePx = allAppsIconTextSizePx,
                iconDrawablePaddingPx = allAppsIconDrawablePaddingPx,
                maxAllAppsTextLineCount = maxAllAppsTextLineCount,
                cellWidthPx = allAppsCellWidthPx,
            )
        }

        /** Re-computes the all-apps cell size to be independent of workspace */
        fun autoResizeAllAppsCells(allAppsProfile: AllAppsProfile): AllAppsProfile {
            val textHeight: Int =
                Utilities.calculateTextHeight(allAppsProfile.iconTextSizePx) *
                    allAppsProfile.maxAllAppsTextLineCount
            return allAppsProfile.copy(
                cellHeightPx =
                    allAppsProfile.iconSizePx +
                        allAppsProfile.iconDrawablePaddingPx +
                        textHeight +
                        (textHeight * 2)
            )
        }

        fun createAllAppsProfile(
            res: Resources,
            inv: InvariantDeviceProfile,
            metric: DisplayMetrics,
            isScalableGrid: Boolean,
            typeIndex: Int,
            scale: Float,
            iconSizePx: Int,
            iconDrawablePaddingOriginalPx: Int,
            cellHeightMultiplier: Float = 1f,
            availableWidthPx: Int,
            visibleColumns: Int,
            isPortrait: Boolean,
        ) =
            when {
                isScalableGrid -> {
                    createAllAppsProfileScalableGrid(
                        inv = inv,
                        metric = metric,
                        typeIndex = typeIndex,
                        scale = scale,
                        iconSizePx = iconSizePx,
                        iconDrawablePaddingOriginalPx = iconDrawablePaddingOriginalPx,
                        cellHeightMultiplier = cellHeightMultiplier,
                        availableWidthPx = availableWidthPx,
                        visibleColumns = visibleColumns,
                        isPortrait = isPortrait,
                    )
                }

                else -> {
                    createAllAppsProfileNonScalable(
                        res, inv, metric, typeIndex, scale,
                        cellHeightMultiplier = cellHeightMultiplier,
                        availableWidthPx = availableWidthPx,
                    )
                }
            }
    }
}
