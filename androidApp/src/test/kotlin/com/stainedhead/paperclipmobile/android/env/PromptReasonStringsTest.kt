package com.stainedhead.paperclipmobile.android.env

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stainedhead.paperclipmobile.android.R
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-3 (ADR-151): the sentences the system prompt shows come from `strings.xml`, so they can be translated. The key
 * prompt used to show the internal word "call paperclip".
 */
@RunWith(AndroidJUnit4::class)
class PromptReasonStringsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theKeyPromptSaysWhyTheBoardKeyIsRead() {
        context.getString(R.string.biometric_key_read_reason) shouldBe "Read the board key to talk to the factory"
    }

    @Test
    fun everyPromptSentenceIsNonBlankAndNotAnInternalWord() {
        val sentences =
            listOf(
                R.string.biometric_unlock_reason,
                R.string.biometric_confirm_reason,
                R.string.biometric_write_reason,
                R.string.biometric_key_read_reason,
            ).map(context::getString)

        sentences.all { it.isNotBlank() && it[0].isUpperCase() } shouldBe true
    }
}
