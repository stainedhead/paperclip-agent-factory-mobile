package com.stainedhead.paperclipmobile.domain

import com.stainedhead.paperclipmobile.domain.port.SecretKey
import com.stainedhead.paperclipmobile.domain.port.SecretName
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ValueObjectsTest {
    @Test
    fun `ids reject blank values`() {
        assertFailsWith<IllegalArgumentException> { InstanceId(" ") }
        assertFailsWith<IllegalArgumentException> { CompanyId("") }
        InstanceId("a").value shouldBe "a"
    }

    @Test
    fun `FR-016 a pin is a base64 SHA-256 digest`() {
        val valid = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="

        SpkiPin.parse(valid)?.value shouldBe valid
        SpkiPin.parse("  $valid ")?.value shouldBe valid
        SpkiPin.parse("too-short") shouldBe null
        SpkiPin.parse("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU") shouldBe null
        SpkiPin.parse("") shouldBe null
    }

    @Test
    fun `secret keys are namespaced per instance`() {
        SecretKey(InstanceId("abc"), SecretName.BoardToken).path shouldBe "instance/abc/boardToken"
        SecretKey.namespacePrefix(InstanceId("abc")) shouldBe "instance/abc/"
        SecretName.entries.map { it.wire } shouldBe
            listOf("boardToken", "clientCertificate", "clientCertificatePassword")
    }

    @Test
    fun `server enums map unknown values to unknown`() {
        DeploymentMode.fromServer("authenticated") shouldBe DeploymentMode.Authenticated
        DeploymentMode.fromServer("local_trusted") shouldBe DeploymentMode.LocalTrusted
        DeploymentMode.fromServer("future") shouldBe DeploymentMode.Unknown
        DeploymentMode.fromServer(null) shouldBe DeploymentMode.Unknown
        Exposure.fromServer("private") shouldBe Exposure.Private
        Exposure.fromServer("public") shouldBe Exposure.Public
        Exposure.fromServer(null) shouldBe Exposure.Unknown
    }
}
