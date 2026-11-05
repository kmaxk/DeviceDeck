package snapshot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdbParsersTest {
    @Test
    fun package_list_parses_name_and_version_code() {
        val out = "package:com.example.app versionCode:42\npackage:com.android.systemui versionCode:34\n"
        assertEquals(mapOf("com.example.app" to "42", "com.android.systemui" to "34"), parsePackageList(out))
    }

    @Test
    fun package_list_ignores_blank_and_malformed_lines() {
        val out = "\nWARNING: linker noise\npackage:com.a versionCode:1\npackage:\npackage:com.b\n"
        assertEquals(mapOf("com.a" to "1", "com.b" to ""), parsePackageList(out))
    }

    @Test
    fun settings_splits_on_first_equals() {
        val out = "a=b=c\nempty=\nno_equals_here\nscreen_off_timeout=60000\n"
        assertEquals(
            mapOf("system/a" to "b=c", "system/empty" to "", "system/screen_off_timeout" to "60000"),
            parseSettings("system", out),
        )
    }

    @Test
    fun settings_prefixes_namespace() {
        assertEquals(mapOf("global/adb_enabled" to "1"), parseSettings("global", "adb_enabled=1"))
    }

    @Test
    fun permissions_parses_granted_and_denied() {
        val dump = """
            Packages:
              Package [com.example.app] (abc123):
                userId=10100
                install permissions:
                  android.permission.INTERNET: granted=true
                User 0: ceDataInode=1 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ USER_SET]
                    android.permission.READ_CONTACTS: granted=false, flags=[ USER_FIXED]
              Package [com.example.plain] (def456):
                userId=10101
                User 0: ceDataInode=2 installed=true
            Shared users:
        """.trimIndent()
        assertEquals(
            mapOf(
                "com.example.app/android.permission.CAMERA" to "granted",
                "com.example.app/android.permission.READ_CONTACTS" to "denied",
            ),
            parsePermissions(dump),
        )
    }

    @Test
    fun props_multiline_value_does_not_shift_following_props() {
        val out = "[ro.a]: [1]\n[ro.multi]: [line one\nline two]\n[ro.b]: [2]\n[ro.empty]: []\n"
        assertEquals(
            mapOf("ro.a" to "1", "ro.multi" to "line one\nline two", "ro.b" to "2", "ro.empty" to ""),
            parseProps(out),
        )
    }

    @Test
    fun volatile_props_are_filtered() {
        val props = mapOf(
            "ro.build.version.sdk" to "34",
            "sys.boot_completed" to "1",
            "init.svc.adbd" to "running",
            "debug.foo" to "x",
            "sys.uptime.now" to "123",
            "ro.boottime.zygote" to "99",
        )
        val filtered = filterVolatileProps(props)
        assertEquals(setOf("ro.build.version.sdk"), filtered.keys)
        assertTrue(filtered.isNotEmpty())
        assertFalse("sys.boot_completed" in filtered)
    }

    @Test
    fun permissions_ignore_hidden_system_packages_section() {
        val dump = """
            Packages:
              Package [com.google.android.youtube] (aaa):
                User 0: ceDataInode=1 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ USER_SET]
            Hidden system packages:
              Package [com.google.android.youtube] (bbb):
                User 0: ceDataInode=1 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=false, flags=[ ]
              Package [com.only.hidden] (ccc):
                User 0: ceDataInode=2 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ ]
            Shared users:
        """.trimIndent()
        assertEquals(mapOf("com.google.android.youtube/android.permission.CAMERA" to "granted"), parsePermissions(dump))
    }

    @Test
    fun permissions_are_keyed_per_user() {
        val dump = """
            Packages:
              Package [com.example.app] (abc):
                User 0: ceDataInode=1 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ ]
                User 10: ceDataInode=2 installed=true
                  runtime permissions:
                    android.permission.CAMERA: granted=false, flags=[ ]
            Shared users:
        """.trimIndent()
        assertEquals(
            mapOf(
                "com.example.app/android.permission.CAMERA" to "granted",
                "com.example.app/android.permission.CAMERA@u10" to "denied",
            ),
            parsePermissions(dump),
        )
    }
}
