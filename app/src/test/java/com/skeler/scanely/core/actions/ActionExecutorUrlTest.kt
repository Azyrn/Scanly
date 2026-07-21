package com.skeler.scanely.core.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionExecutorUrlTest {

    @Test
    fun `keeps geo uri unchanged`() {
        assertEquals("geo:36.1,-115.1", ensureScheme("geo:36.1,-115.1"))
    }

    @Test
    fun `keeps mailto uri unchanged`() {
        assertEquals("mailto:a@b.com", ensureScheme("mailto:a@b.com"))
    }

    @Test
    fun `keeps https url unchanged`() {
        assertEquals("https://x.com", ensureScheme("https://x.com"))
    }

    @Test
    fun `keeps http url unchanged`() {
        assertEquals("http://x.com", ensureScheme("http://x.com"))
    }

    @Test
    fun `lowercases http scheme and preserves path case`() {
        assertEquals("http://x.com/Path", ensureScheme("HTTP://x.com/Path"))
    }

    @Test
    fun `lowercases geo scheme`() {
        assertEquals("geo:36.1,-115.1", ensureScheme("GEO:36.1,-115.1"))
    }

    @Test
    fun `adds https to a bare www host`() {
        assertEquals("https://www.example.com", ensureScheme("www.example.com"))
    }

    @Test
    fun `adds https to a host with a port`() {
        assertEquals("https://example.com:8080/p", ensureScheme("example.com:8080/p"))
    }
}
