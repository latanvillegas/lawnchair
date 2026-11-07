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

        /** Responsive drawer rows scroll vertically; grow their height instead of shrinking icons. */
        fun calculateResponsiveRowHeightPx(
            specHeightPx: Int,
            iconSizePx: Int,
            drawablePaddingPx: Int,
            textHeightPx: Int,
            maxTextLines: Int,
            gutterPx: Int,
            rowHeightFactor: Float = 1f,
        ): Int {
            // A responsive grid must honor the user's row-height slider. Never shrink
            // below the content's height: the drawer can scroll instead of clipping.
            val safeFactor = if (rowHeightFactor.isFinite()) {
                rowHeightFactor.coerceIn(0.3f, 1.5f)
            } else {
                1f
            }
            val desiredHeightPx = max(1, (specHeightPx * safeFactor).roundToInt())
            val requiredHeight = iconSizePx + drawablePaddingPx +
                textHeightPx * (max(0, maxTextLines) + 2)
            return max(desiredHeightPx, requiredHeight) + max(0, gutterPx)
        }

        /**
         * Honor the icon-size slider while using the actual grid column pitch.
         *
         * Responsive specs can reserve much of a column for a gutter, particularly
         * in portrait tablet mode. RecyclerView gives each icon a full grid span,
         * so limiting the image to spec.cellSizePx alone incorrectly caps 250%
         * icons even when the row has ample horizontal space.
         *
         * preferredSizePx is the IDP's already-scaled user setting: do not apply
         * factor to it a second time. Preserve at least 10% of the grid span for
         * separation and avoid allocating beyond the measured column width.
         */
        fun fitResponsiveIconSizePx(
            baseSizePx: Int,
            factor: Float,
            columnWidthPx: Int,
            columnGutterPx: Int = 0,
            preferredSizePx: Int = 0,
        ): Int {
            val safeFactor = if (factor.isFinite()) factor.coerceIn(0.5f, 2.5f) else 1f
            val requested = max(
                max(1, (baseSizePx * safeFactor).roundToInt()),
                preferredSizePx.coerceAtLeast(0),
            )
            val cellWidth = max(1, columnWidthPx)
            val gutter = columnGutterPx.coerceAtLeast(0)
            val spanBudget = cellWidth.toLong() + gutter.toLong()
            val safeBudget = if (gutter > 0) {
                min(
                    Int.MAX_VALUE.toLong(),
                    max(cellWidth.toLong(), spanBudget * 9L / 10L),
                ).toInt()
            } else {
                cellWidth
            }
            return min(requested, safeBudget)
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
        ): AllAppsProfile {
            val allAppsBorderSpacePx = calculateAllAppsBorderSpacePx(inv, metric, typeIndex, scale)
            var allAppsCellHeightPx =
                (pxFromDp(inv.allAppsCellSize[typeIndex].y, metric, cellHeightMultiplier) + allAppsBorderSpacePx.y)
            var allAppsIconSizePx = pxFromDp(inv.allAppsIconSize[typeIndex], metric)
            val allAppsIconTextSizePx =
                pxFromSp(inv.allAppsIconTextSize[typeIndex], metric).toFloat()
            val allAppsIconDrawablePaddingPx =
                max(
                    0,
                    (iconDrawablePaddingOriginalPx -
                        ((iconSizePx - getIconVisibleSizePx(iconSizePx)) / 2)),
                )
            var allAppsCellWidthPx = pxFromDp(inv.allAppsCellSize[typeIndex].x, metric, scale)

            if (allAppsCellWidthPx < allAppsIconSizePx) {
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
            rowHeightFactor: Float,
            preferredIconSizePx: Int,
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
                responsiveAllAppsWidthSpec.gutterPx,
                preferredIconSizePx,
            )
            val allAppsIconTextSizePx = responsiveAllAppsCellSpec.iconTextSize.toFloat()
            val availableSpanPx = max(
                allAppsCellWidthPx,
                min(
                    Int.MAX_VALUE.toLong(),
                    allAppsCellWidthPx.toLong() +
                        responsiveAllAppsWidthSpec.gutterPx.coerceAtLeast(0).toLong(),
                ).toInt(),
            )
            val allAppsIconDrawablePaddingPx = min(
                max(0, (availableSpanPx - allAppsIconSizePx) / 2),
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
                rowHeightFactor,
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
