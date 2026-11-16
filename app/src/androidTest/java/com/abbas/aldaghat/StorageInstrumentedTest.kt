package com.abbas.aldaghat

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.abbas.aldaghat.data.MediaStorePublisher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Verifies the Movies/ABBAS publishing rules on a real device. */
@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {

    private lateinit var context: Context
    private lateinit var publisher: MediaStorePublisher

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        publisher = MediaStorePublisher(context)
    }

    private fun sampleVideo(): File {
        val source = TestVideoFactory.create(
            context, "storage_input.mp4", width = 640, height = 360, durationSeconds = 1,
            withAudio = false, videoBitrate = 800_000,
        )
        return source
    }

    @Test
    fun publishesIntoAbbasFolderWithUniqueName() {
        runBlocking {
        val source = sampleVideo()
        val first = publisher.publish(source, "abbas_test")
        val second = publisher.publish(source, "abbas_test")

        assertNotNull(first.uri)
        assertTrue(first.displayName.startsWith("abbas_test"))
        assertTrue(first.displayName.endsWith(".mp4"))
        assertTrue(
            "duplicate names must be avoided (${first.displayName} vs ${second.displayName})",
            first.displayName != second.displayName,
        )
        assertTrue(first.sizeBytes > 0)

        val listed = publisher.listAbbasVideos()
        assertTrue(
            "published file must be visible in the ABBAS listing",
            listed.any { it.displayName == first.displayName },
        )
        assertTrue(listed.any { it.displayName == second.displayName })

        assertTrue(publisher.delete(first.uri))
        assertTrue(publisher.delete(second.uri))
        source.delete()
        }
    }

    @Test
    fun detectsInsufficientSpace() {
        assertFalse(publisher.hasEnoughSpace(2_000_000_000_000L))
        assertTrue(publisher.hasEnoughSpace(1024L))
    }

    @Test
    fun relativePathPointsToMoviesAbbas() {
        assertTrue(publisher.abbasRelativePath().endsWith("ABBAS"))
    }
}
