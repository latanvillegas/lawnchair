/*
 * Copyright (C) 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */
package app.lawnchair.icons.picker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ImportedIconStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun unconfirmedImageIsRemovedFromAppStorage() {
        val imageName = UUID.randomUUID().toString() + ".png"
        val file = ImportedIconStore.resolve(context, imageName)
        file.parentFile!!.mkdirs()
        assertThat(file.createNewFile()).isTrue()
        assertThat(ImportedIconStore.discardUnconfirmed(context, imageName)).isTrue()
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun pathTraversalCannotDeleteUnrelatedFiles() {
        assertThat(ImportedIconStore.discardUnconfirmed(context, "../outside.png")).isFalse()
    }
}
