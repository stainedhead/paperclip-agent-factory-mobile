package com.stainedhead.paperclipmobile.application

import com.stainedhead.paperclipmobile.domain.BaseUriError
import com.stainedhead.paperclipmobile.domain.BaseUriResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class ValidateInstanceAddressTest {
    @Test
    fun `accepts and normalises an https address`() {
        val result = ValidateInstanceAddress(allowInsecureLocal = false)(" https://Paperclip.example.com/ ")

        result.shouldBeInstanceOf<BaseUriResult.Valid>().uri.value shouldBe "https://paperclip.example.com"
    }

    @Test
    fun `refuses local http in a release configuration`() {
        val result = ValidateInstanceAddress(allowInsecureLocal = false)("http://localhost:3100")

        result.shouldBeInstanceOf<BaseUriResult.Invalid>().error shouldBe BaseUriError.InsecureHttp
    }

    @Test
    fun `accepts local http in a debug configuration`() {
        val result = ValidateInstanceAddress(allowInsecureLocal = true)("http://localhost:3100")

        result.shouldBeInstanceOf<BaseUriResult.Valid>().uri.value shouldBe "http://localhost:3100"
    }
}
