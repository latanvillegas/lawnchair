/*
 * Copyright (C) 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package app.lawnchair.ui.preferences

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PreferencesResponsiveLayoutTest {

    @Test
    fun narrowFloatingWindowMustNotUseSplitPane() {
        assertThat(canShowSettingsTwoPane(540f, 900f)).isFalse()
    }

    @Test
    fun portraitWindowMustNotOverflowIntoSecondPane() {
        assertThat(canShowSettingsTwoPane(760f, 1100f)).isFalse()
    }

    @Test
    fun wideTabletSupportsTwoPanelsWithUsableSecondPanel() {
        assertThat(canShowSettingsTwoPane(1200f, 820f)).isTrue()
        assertThat(canShowSettingsTwoPane(900f, 480f)).isTrue()
    }

    @Test
    fun minimizedDesktopWindowFallsBackToOnePane() {
        assertThat(canShowSettingsTwoPane(940f, 460f)).isFalse()
    }

    @Test
    fun invalidViewportNeverProducesOversizedLayout() {
        assertThat(canShowSettingsTwoPane(Float.NaN, 720f)).isFalse()
        assertThat(canShowSettingsTwoPane(Float.POSITIVE_INFINITY, 720f)).isFalse()
        assertThat(canShowSettingsTwoPane(-5f, 720f)).isFalse()
    }
}
