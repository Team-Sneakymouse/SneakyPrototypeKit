package net.sneakyprototypekit.util

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import java.nio.file.Files
import java.nio.file.Path

class DatabaseMigrationTest {
    @Test
    fun `Paper loads the joined SneakyDB API instead of PocketBase`() {
        val descriptor = Files.readString(Path.of("src/main/resources/paper-plugin.yml"))
        assertContains(descriptor, "SneakyDB:\n      load: BEFORE\n      required: true\n      join-classpath: true")
        assertFalse(descriptor.contains("SneakyPocketbase"))
    }
}
