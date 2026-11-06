package com.stainedhead.paperclipmobile.umbrella

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** AND-002: the registry behind `AndroidAuthHost` holds the resumed activity and nothing else. */
class ResumedReferenceTest {
    private val first = Any()
    private val second = Any()

    @Test
    fun `nothing is registered until an activity resumes`() {
        ResumedReference<Any>().current().shouldBeNull()
    }

    @Test
    fun `the resumed activity is the current one and a newer one replaces it`() {
        val reference = ResumedReference<Any>()

        reference.register(first)
        reference.current() shouldBe first
        reference.register(second)
        reference.current() shouldBe second
    }

    @Test
    fun `an activity that pauses clears itself but a late pause of an older one does not clear the newer`() {
        val reference = ResumedReference<Any>()
        reference.register(first)
        reference.register(second)

        reference.unregister(first)
        reference.current() shouldBe second
        reference.unregister(second)
        reference.current().shouldBeNull()
    }
}
