package com.stainedhead.paperclipmobile.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.Test

class BaseUriTest {
    private fun valid(
        input: String,
        allowInsecureLocal: Boolean = false,
    ): String {
        val result = BaseUri.parse(input, allowInsecureLocal)
        return result.shouldBeInstanceOf<BaseUriResult.Valid>().uri.value
    }

    private fun invalid(
        input: String,
        allowInsecureLocal: Boolean = false,
    ): BaseUriError {
        val result = BaseUri.parse(input, allowInsecureLocal)
        return result.shouldBeInstanceOf<BaseUriResult.Invalid>().error
    }

    @Test
    fun `trims whitespace and removes a trailing slash`() {
        valid("  https://paperclip.example.com/  ") shouldBe "https://paperclip.example.com"
    }

    @Test
    fun `lower-cases scheme and host but not the path`() {
        valid("HTTPS://Paperclip.Example.COM/Paperclip") shouldBe "https://paperclip.example.com/Paperclip"
    }

    @Test
    fun `keeps a path prefix for reverse-proxied installs`() {
        valid("https://example.com/paperclip/") shouldBe "https://example.com/paperclip"
    }

    @Test
    fun `allows an explicit port`() {
        valid("https://paperclip.tail1234.ts.net:3100") shouldBe "https://paperclip.tail1234.ts.net:3100"
    }

    @Test
    fun `rejects blank input`() {
        invalid("   ") shouldBe BaseUriError.Empty
    }

    @Test
    fun `rejects a missing scheme`() {
        invalid("paperclip.example.com") shouldBe BaseUriError.MissingScheme
    }

    @Test
    fun `rejects unsupported schemes`() {
        invalid("ftp://example.com") shouldBe BaseUriError.UnsupportedScheme
    }

    @Test
    fun `rejects http for remote hosts even when local http is allowed`() {
        invalid("http://example.com", allowInsecureLocal = true) shouldBe BaseUriError.InsecureHttp
    }

    @Test
    fun `rejects http when local http is not allowed`() {
        invalid("http://localhost:3100") shouldBe BaseUriError.InsecureHttp
    }

    @Test
    fun `allows http for localhost and the Android emulator host when local http is allowed`() {
        valid("http://localhost:3100", allowInsecureLocal = true) shouldBe "http://localhost:3100"
        valid("http://10.0.2.2:3100", allowInsecureLocal = true) shouldBe "http://10.0.2.2:3100"
    }

    @Test
    fun `rejects a missing host`() {
        invalid("https://") shouldBe BaseUriError.MissingHost
        invalid("https:///path") shouldBe BaseUriError.MissingHost
    }

    @Test
    fun `rejects user info`() {
        invalid("https://user:secret@example.com") shouldBe BaseUriError.UserInfoNotAllowed
    }

    @Test
    fun `rejects an invalid port`() {
        invalid("https://example.com:abc") shouldBe BaseUriError.InvalidPort
        invalid("https://example.com:70000") shouldBe BaseUriError.InvalidPort
    }

    @Test
    fun `rejects query strings and fragments`() {
        invalid("https://example.com?x=1") shouldBe BaseUriError.QueryOrFragmentNotAllowed
        invalid("https://example.com/#top") shouldBe BaseUriError.QueryOrFragmentNotAllowed
    }

    @Test
    fun `rejects hosts with invalid characters`() {
        invalid("https://exa mple.com") shouldBe BaseUriError.InvalidHost
    }

    @Test
    fun `api base is the base uri plus api`() {
        val uri = BaseUri.parse("https://example.com/paperclip").shouldBeInstanceOf<BaseUriResult.Valid>().uri
        uri.apiBase shouldBe "https://example.com/paperclip/api"
    }
}
