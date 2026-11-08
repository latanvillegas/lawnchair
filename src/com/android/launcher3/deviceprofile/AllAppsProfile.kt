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

        /**
         * The maximum physical width of one visible All Apps grid column.
         * This uses the effective columns, not the stored grid column count:
         * the latter may be reduced in portrait when the user requests 250%.
         */
        fun portraitColumnWidthBudget(
            availableWidthPx: Int,
            visibleColumns: Int,
            edgeInsetPx: Int,
        ): Int {
            val columns = visibleColumns.coerceAtLeast(1)
            val insets = edgeInsetPx.coerceAtLeast(0)
            return max(1, ((availableWidthPx.toLong() - 2L * insets)
                .coerceAtLeast(1L) / columns).toInt())
        }

        /**
         * Return a visible column count that can display the requested size.
         * This is a runtime layout value, never written to the user's setting.
         * Portrait only; DeviceProfile deliberately skips it in landscape.
         */
        fun portraitColumnsForIcon(
            configuredColumns: Int,
            availableWidthPx: Int,
            iconSizePx: Int,
            scaleFactor: Float,
            edgeInsetPx: Int,
        ): Int {
            val columns = configuredColumns.coerceAtLeast(1)
            if (!scaleFactor.isFinite() || scaleFactor <= 1f ||
                availableWidthPx <= 0 || iconSizePx <= 0
            ) return columns
            val usable = (availableWidthPx.toLong() -
                2L * edgeInsetPx.coerceAtLeast(0)).coerceAtLeast(1)
            // Keep a small gap (up to 8 px) without sacrificing icon size.
            val gap = min(8, max(0, iconSizePx / 16))
            return min(columns, max(1, ((usable + gap) / (iconSizePx.toLong() + gap)).toInt()))
        }

        /**
         * Expand a portrait tablet's 96dp XML cell to the requested icon size,
         * subject to the physical width available per visible column.
         * At 60dp base, 150%=90dp and 250%=150dp.
         */
        fun fitPortraitScalableIcon(
            requestedSizePx: Int,
            baseCellWidthPx: Int,
            availableWidthPx: Int,
            visibleColumns: Int,
            edgeInsetPx: Int,
        ): Int {
            val limit = portraitColumnWidthBudget(availableWidthPx, visibleColumns, edgeInsetPx)
            return min(max(1, requestedSizePx), max(1, limit))
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
            portrait: Boolean,
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

            if (portrait) {
                // An XML cell width is a baseline, not a cap on 250% icons.
                // Stretch each cell into available grid span without altering
                // the row-height slider or the stored user column count.
                val requested = allAppsIconSizePx.coerceAtLeast(1)
                allAppsIconSizePx = fitPortraitScalableIcon(
                    requested,
                    allAppsCellWidthPx,
                    availableWidthPx,
                    visibleColumns,
                    pxFromDp(8f, metric),
                )
                allAppsCellWidthPx = min(
                    max(allAppsCellWidthPx, allAppsIconSizePx),
                    portraitColumnWidthBudget(
                        availableWidthPx, visibleColumns, pxFromDp(8f, metric)),
                )
                val columns = visibleColumns.coerceAtLeast(1)
                val freeWidth = (availableWidthPx.toLong() - columns.toLong() *
                    allAppsCellWidthPx.toLong()).coerceAtLeast(0L)
                allAppsBorderSpacePx.x = if (columns > 1) {
                    min(allAppsBorderSpacePx.x.coerceAtLeast(0),
                        (freeWidth / (columns - 1)).toInt())
                } else {
                    0
                }
                allAppsIconDrawablePaddingPx = min(
                    allAppsIconDrawablePaddingPx,
                    max(0, (allAppsCellWidthPx - allAppsIconSizePx) / 2),
                )
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
            visibleColumns: Int,
            portrait: Boolean,
        ): AllAppsProfile {
            val allAppsBorderSpacePx = calculateAllAppsBorderSpacePx(inv, metric, typeIndex, scale)
            // The number of columns can be set as high as ten even on narrow phones.
            // Bound icon padding as well as the bitmap so every row fits its real width.
            val columns = if (portrait) visibleColumns.coerceAtLeast(1)
                else inv.numAllAppsColumns.coerceAtLeast(1)
            val columnWidthBudget = max(1, availableWidthPx / columns)
            val requestedIconPx = max(1, pxFromDp(inv.allAppsIconSize[typeIndex], metric, scale))
            // In portrait, favor the requested icon over decorative padding.
            // The old code always reserved the padding first, capping icon growth.
            val allAppsIconDrawablePaddingPx = if (portrait) {
                min(
                    max(0, res.getDimensionPixelSize(R.dimen.all_apps_icon_drawable_padding)),
                    max(0, (columnWidthBudget - requestedIconPx) / 2),
                )
            } else {
                // Keep the original landscape padding calculation.
                min(
                    max(0, res.getDimensionPixelSize(R.dimen.all_apps_icon_drawable_padding)),
                    max(0, (columnWidthBudget - 1) / 2),
                )
            }
            val iconWidthBudget = max(1, columnWidthBudget - 2 * allAppsIconDrawablePaddingPx)
            val allAppsIconSizePx = min(requestedIconPx, iconWidthBudget)
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
            portrait: Boolean,
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
                        portrait = portrait,
                    )
                }

                else -> {
                    createAllAppsProfileNonScalable(
                        res, inv, metric, typeIndex, scale,
                        cellHeightMultiplier = cellHeightMultiplier,
                        availableWidthPx = availableWidthPx,
                        visibleColumns = visibleColumns,
                        portrait = portrait,
                    )
                }
            }
    }
}
