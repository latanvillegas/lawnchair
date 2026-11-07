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
import app.lawnchair.ui.preferences.data.liveinfo.LiveInformationManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LiveInformationParsingTest {

    @Test
    fun malformedStoredJsonFallsBackToEmptyDismissalSet() {
        assertThat(LiveInformationManager.parseDismissedAnnouncementIds("{invalid json"))
            .isEmpty()
    }

    @Test
    fun emptyArrayParsesWithoutFailure() {
        assertThat(LiveInformationManager.parseDismissedAnnouncementIds("[]"))
            .isEmpty()
    }
}
