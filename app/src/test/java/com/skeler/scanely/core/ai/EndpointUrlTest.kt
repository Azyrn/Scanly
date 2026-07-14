package com.skeler.scanely.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointUrlTest {

    @Test
    fun `keeps a full chat completions url`() {
        assertEquals(
            "https://host.example/v1/chat/completions",
            EndpointUrl.normalize("https://host.example/v1/chat/completions")
        )
    }

    @Test
    fun `forces https on a scheme-less host so the key never leaks to the base url`() {
        assertEquals(
            "https://host.example/v1/chat/completions",
            EndpointUrl.normalize("host.example/v1/chat/completions")
        )
    }

    @Test
    fun `forces https on a scheme-less host and port`() {
        assertEquals(
            "https://192.168.1.5:1234/v1/chat/completions",
            EndpointUrl.normalize("192.168.1.5:1234/v1/chat/completions")
        )
    }

    @Test
    fun `completes a base url that stops at v1`() {
        assertEquals(
            "https://host.example/v1/chat/completions",
            EndpointUrl.normalize("https://host.example/v1/")
        )
    }

    @Test
    fun `rejects a non-http scheme`() {
        assertNull(EndpointUrl.normalize("ftp://host.example/v1/chat/completions"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(EndpointUrl.normalize("   "))
    }

    @Test
    fun `derives the models url for key verification`() {
        assertEquals(
            "https://host.example/v1/models",
            EndpointUrl.modelsUrl("https://host.example/v1/chat/completions")
        )
    }
}
