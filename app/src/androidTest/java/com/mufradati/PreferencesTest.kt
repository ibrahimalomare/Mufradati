package com.mufradati

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesTest {
    @Test
    fun preferencesSurviveRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = java.io.File(context.cacheDir, "persistence-test.preferences_pb")
        file.delete()
        val key = stringPreferencesKey("sort")
        val job1 = SupervisorJob()
        val first =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(job1 + Dispatchers.IO),
                produceFile = { file },
            )
        first.edit { it[key] = "NEWEST" }
        job1.cancelAndJoin()
        val job2 = SupervisorJob()
        try {
            val second =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(job2 + Dispatchers.IO),
                    produceFile = { file },
                )
            assertEquals("NEWEST", second.data.first()[key])
        } finally {
            job2.cancelAndJoin()
            file.delete()
        }
    }
}
