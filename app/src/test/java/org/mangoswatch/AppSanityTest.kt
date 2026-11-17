package org.mangoswatch

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppSanityTest {
    @Test
    fun testAppPackageName() {
        val packageName = "org.mangoswatch"
        assertNotNull(packageName)
    }
}
