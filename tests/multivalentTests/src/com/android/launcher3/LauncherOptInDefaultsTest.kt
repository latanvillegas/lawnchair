/*
 * Copyright (C) 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feature defaults are opt-in. Persisted DataStore/SharedPreferences values
 * still take precedence over these resources for existing users.
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherOptInDefaultsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun optionalUiFeaturesStartDisabled() {
        val r = context.resources
        val disabledByDefault = intArrayOf(
            R.bool.config_default_show_hotseat,
            R.bool.config_default_show_status_bar,
            R.bool.config_default_show_top_shadow,
            R.bool.config_default_rounded_widgets,
            R.bool.config_default_search_bar_background,
            R.bool.config_default_show_scrollbar,
            R.bool.config_default_show_suggested_apps_at_drawer_top,
            R.bool.config_default_enable_smartspace,
            R.bool.config_default_live_information_enabled,
            R.bool.config_default_live_information_show_announcements,
        )
        for (id in disabledByDefault) {
            assertThat(r.getBoolean(id)).isFalse()
        }
    }

    @Test
    fun searchWidgetsAreOptIn() {
        val r = context.resources
        assertThat(r.getString(R.string.config_default_hotseat_mode)).isEqualTo("disabled")
        // This control is intentionally inverted: true means the drawer bar is hidden.
        assertThat(r.getBoolean(R.bool.config_default_hide_app_drawer_search_bar)).isTrue()
    }
}
