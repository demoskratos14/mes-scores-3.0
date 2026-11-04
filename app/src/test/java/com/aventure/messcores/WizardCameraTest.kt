package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La caméra d'une prise (style sorcier) : pas de caméra pour un déplacement, à plat au début et à la fin, inclinée et rapprochée au milieu. */
class WizardCameraTest {

    // Un cavalier blanc (case 62, g1) prend un pion noir sur la case 45 (f3).
    private val capture = MoveFx(from = 62, to = 45, mover = 'N', captured = 'p', capturedSquare = 45)
    private val quiet = MoveFx(from = 52, to = 36, mover = 'P', captured = '.', capturedSquare = 36)

    @Test
    fun aSimpleMoveHasNoCamera() {
        assertNull(wizardCamera(quiet, 0.5f, flipped = false))
    }

    @Test
    fun theBoardIsFlatAtTheStartAndAtTheEnd() {
        for (progress in listOf(0f, 1f)) {
            val camera = wizardCamera(capture, progress, flipped = false)
            assertNotNull(camera)
            assertEquals(0f, camera!!.tiltDeg, 0.001f)
            assertEquals(0f, camera.orbitDeg, 0.001f)
            assertEquals(1f, camera.zoom, 0.001f)
        }
    }

    @Test
    fun theCameraIsTiltedAndZoomedBeforeTheImpact() {
        val camera = wizardCamera(capture, 0.4f, flipped = false)!!
        assertTrue(camera.tiltDeg > 0f)
        assertTrue(camera.zoom > 1f)
    }

    @Test
    fun theImpactShakesTheBoard() {
        val camera = wizardCamera(capture, 0.50f, flipped = false)!!
        assertTrue(camera.shakeX != 0f || camera.shakeY != 0f)
        val calm = wizardCamera(capture, 0.60f, flipped = false)!!
        assertEquals(0f, calm.shakeX, 0.0001f)
        assertEquals(0f, calm.shakeY, 0.0001f)
    }

    @Test
    fun theCameraFocusesOnTheVictimSquare() {
        // Case 45 : rangée 5, colonne 5.
        val camera = wizardCamera(capture, 0.4f, flipped = false)!!
        assertEquals(5.5f / 8f, camera.pivotX, 0.001f)
        assertEquals(5.5f / 8f, camera.pivotY, 0.001f)
        // Plateau retourné : la case est vue de l'autre côté.
        val flipped = wizardCamera(capture, 0.4f, flipped = true)!!
        assertEquals(2.5f / 8f, flipped.pivotX, 0.001f)
        assertEquals(2.5f / 8f, flipped.pivotY, 0.001f)
    }
}
