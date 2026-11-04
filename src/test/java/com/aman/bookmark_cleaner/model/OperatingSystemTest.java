package com.aman.bookmark_cleaner.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OperatingSystemTest {

    @Test
    @DisplayName("Windows detection from os.name")
    void testWindowsDetection() {
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.fromOsName("Windows 10"));
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.fromOsName("Windows 11"));
        assertEquals(OperatingSystem.WINDOWS, OperatingSystem.fromOsName("Windows Server 2022"));
    }

    @Test
    @DisplayName("Linux detection from os.name")
    void testLinuxDetection() {
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromOsName("Linux"));
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromOsName("Ubuntu Linux"));
        assertEquals(OperatingSystem.LINUX, OperatingSystem.fromOsName("AIX"));
    }

    @Test
    @DisplayName("macOS detection from os.name")
    void testMacDetection() {
        assertEquals(OperatingSystem.MACOS, OperatingSystem.fromOsName("Mac OS X"));
        assertEquals(OperatingSystem.MACOS, OperatingSystem.fromOsName("macOS"));
        assertEquals(OperatingSystem.MACOS, OperatingSystem.fromOsName("Darwin"));
    }

    @Test
    @DisplayName("Unknown operating system handling")
    void testUnknownOsDetection() {
        assertEquals(OperatingSystem.UNKNOWN, OperatingSystem.fromOsName(null));
        assertEquals(OperatingSystem.UNKNOWN, OperatingSystem.fromOsName(""));
        assertEquals(OperatingSystem.UNKNOWN, OperatingSystem.fromOsName("Solaris"));
        assertEquals(OperatingSystem.UNKNOWN, OperatingSystem.fromOsName("FreeBSD"));
    }

    @Test
    @DisplayName("getCurrent returns a non-null operating system")
    void testGetCurrent() {
        assertNotNull(OperatingSystem.getCurrent());
    }
}
