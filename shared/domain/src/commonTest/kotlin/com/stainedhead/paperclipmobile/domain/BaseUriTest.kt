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

    @Test
    fun `rejects whitespace control characters and backslashes in the path`() {
        listOf(
            "https://h.example/a b",
            "https://h.example/a\tb",
            "https://h.example/a\u0007b",
            "https://h.example/a\\b",
        ).forEach { invalid(it) shouldBe BaseUriError.InvalidPath }
    }

    @Test
    fun `rejects dot segments empty segments and unsafe percent-encoding in the path`() {
        listOf(
            "https://h.example/..",
            "https://h.example/a/../b",
            "https://h.example/./a",
            "https://h.example/%2e%2E/a",
            "https://h.example/a//b",
            "https://h.example//a",
            "https://h.example/%0d",
            "https://h.example/%00",
            "https://h.example/%2f",
            "https://h.example/%5C",
            "https://h.example/%zz",
            "https://h.example/100%",
            "https://h.example/%4",
        ).forEach { invalid(it) shouldBe BaseUriError.InvalidPath }
    }

    @Test
    fun `accepts ordinary prefixes and well-formed percent-encoding`() {
        valid("https://h.example/paperclip/v1.2/") shouldBe "https://h.example/paperclip/v1.2"
        valid("https://h.example/my%20factory") shouldBe "https://h.example/my%20factory"
        valid("https://h.example/a-b_c~d/.well-known") shouldBe "https://h.example/a-b_c~d/.well-known"
    }

    @Test
    fun `rejects empty host labels hyphen edges a trailing dot and long labels`() {
        listOf(
            "https://a..b",
            "https://a-.b",
            "https://-a.b",
            "https://.a.b",
            "https://example.com.",
            "https://${"a".repeat(64)}.example",
        ).forEach { invalid(it) shouldBe BaseUriError.InvalidHost }
        valid("https://${"a".repeat(63)}.example") shouldBe "https://${"a".repeat(63)}.example"
    }

    @Test
    fun `accepts punycode but rejects unicode hosts`() {
        valid("https://xn--bcher-kva.example") shouldBe "https://xn--bcher-kva.example"
        invalid("https://b\u00fccher.example") shouldBe BaseUriError.InvalidHost
    }

    @Test
    fun `validates IPv4 literals`() {
        valid("https://192.168.1.20:3100") shouldBe "https://192.168.1.20:3100"
        listOf("https://999.1.1.1", "https://1.2.3", "https://1.2.3.4.5", "https://01.2.3.4", "https://1.2.3.256")
            .forEach { invalid(it) shouldBe BaseUriError.InvalidHost }
    }

    @Test
    fun `validates IPv6 literals`() {
        valid("https://[::1]:3100/p") shouldBe "https://[::1]:3100/p"
        valid("https://[FD7A:115C:A1E0::1]") shouldBe "https://[fd7a:115c:a1e0::1]"
        valid("https://[::ffff:192.168.1.1]") shouldBe "https://[::ffff:192.168.1.1]"
        listOf(
            "https://[::1::2]",
            "https://[12345::1]",
            "https://[1:2:3:4:5:6:7:8:9]",
            "https://[:::]",
            "https://[1:2]",
            "https://[]",
            "https://[g::1]",
        ).forEach { invalid(it) shouldBe BaseUriError.InvalidHost }
    }
}
