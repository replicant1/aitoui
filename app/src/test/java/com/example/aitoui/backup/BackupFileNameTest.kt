package com.example.aitoui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileNameTest {

    @Test
    fun `default name is pxtx, an 8-digit date, the db version, and a zip extension`() {
        assertTrue(BackupFileName.default(schemaVersion = 26, nowMillis = 0L).matches(Regex("""pxtx-\d{8}-db26\.zip""")))
        assertTrue(BackupFileName.default(schemaVersion = 30, nowMillis = 0L).contains("-db30."))
    }

    @Test
    fun `ensureZip appends the extension only when missing`() {
        assertEquals("backup.zip", BackupFileName.ensureZip("backup"))
        assertEquals("backup.zip", BackupFileName.ensureZip("backup.zip"))
        assertEquals("backup.ZIP", BackupFileName.ensureZip("backup.ZIP")) // case-insensitive, left as typed
        assertEquals("spaced.zip", BackupFileName.ensureZip("  spaced  "))
    }

    @Test
    fun `isValid requires a non-blank base and no path separators`() {
        assertTrue(BackupFileName.isValid("mybackup"))
        assertTrue(BackupFileName.isValid("pxtx-21072026-db26.zip"))
        assertFalse(BackupFileName.isValid(""))
        assertFalse(BackupFileName.isValid("   "))
        assertFalse(BackupFileName.isValid(".zip"))
        assertFalse(BackupFileName.isValid("dir/file.zip"))
        assertFalse(BackupFileName.isValid("a\\b"))
    }
}
