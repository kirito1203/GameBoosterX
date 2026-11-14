package com.gamebooster.x.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboTest {

    @Test fun validatesPackages() {
        assertTrue(TurboCommands.isValidPackage("com.mojang.minecraftpe"))
        assertTrue(TurboCommands.isValidPackage("com.dts.freefireth"))
        assertFalse(TurboCommands.isValidPackage("com.x; rm -rf /"))
        assertFalse(TurboCommands.isValidPackage("semponto"))
        assertFalse(TurboCommands.isValidPackage("com.a b"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInjection() {
        TurboCommands.apply("com.x && reboot", TurboSettings.DEFAULT)
    }

    @Test fun formatScale() {
        assertEquals("0.85", TurboCommands.formatScale(0.85f))
        assertEquals("0.7", TurboCommands.formatScale(0.7f))
        assertEquals("0.5", TurboCommands.formatScale(0.5f))
        assertEquals("1", TurboCommands.formatScale(1.0f))
    }

    @Test fun applyBuildsInterventions() {
        val cmds = TurboCommands.apply("com.mojang.minecraftpe", TurboSettings(0.7f, 60))
        assertEquals(
            listOf(
                "am kill-all",
                "cmd game mode performance com.mojang.minecraftpe",
                "cmd game set --mode 2 --downscale 0.7 --fps 60 com.mojang.minecraftpe",
                "cmd game list-configs com.mojang.minecraftpe",
            ),
            cmds,
        )
    }

    @Test fun applyWithoutInterventionsSkipsSet() {
        val cmds = TurboCommands.apply("com.roblox.client", TurboSettings(1.0f, 0))
        assertFalse(cmds.any { it.startsWith("cmd game set") })
        assertTrue(cmds.contains("cmd game mode performance com.roblox.client"))
    }

    @Test fun onlyFpsCap() {
        val cmds = TurboCommands.apply("com.roblox.client", TurboSettings(1.0f, 30))
        assertTrue(cmds.contains("cmd game set --mode 2 --fps 30 com.roblox.client"))
    }

    @Test fun restore() {
        assertEquals(
            listOf("cmd game reset com.roblox.client", "cmd game mode standard com.roblox.client"),
            TurboCommands.restore("com.roblox.client"),
        )
    }

    @Test fun shellResultOk() {
        assertTrue(ShellResult("x", 0, "").ok)
        assertFalse(ShellResult("x", 1, "").ok)
        assertFalse(ShellResult("x", 0, "java.lang.SecurityException: denied").ok)
        assertFalse(ShellResult("x", 0, "Unknown command: set").ok)
    }

    @Test fun defaults() {
        assertTrue(TurboSettings.SCALE_OPTIONS.contains(TurboSettings.DEFAULT.resolutionScale))
        assertTrue(TurboSettings.FPS_OPTIONS.contains(TurboSettings.DEFAULT.fpsCap))
    }
}
