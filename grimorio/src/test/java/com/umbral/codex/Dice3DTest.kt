package com.umbral.codex

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.umbral.codex.ui.components.DieMesh
import com.umbral.codex.ui.components.V3
import com.umbral.codex.ui.components.restingRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Geometria dos dados 3D: faces certas, rótulos completos, opostos somando N+1 e resultado de frente. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Dice3DTest {
    private val expected = mapOf(4 to 4, 6 to 6, 8 to 8, 10 to 10, 12 to 12, 20 to 20)

    @Test fun meshesHaveRightFacesAndLabels() {
        expected.forEach { (sides, faces) ->
            val m = DieMesh.of(sides)
            assertEquals("faces do d$sides", faces, m.faces.size)
            assertEquals("rótulos do d$sides", (1..sides).map { "$it" }.toSet(), m.labels.toSet())
            // Faces planas e convexas: todos os vértices da face na mesma altura ao longo da normal
            m.faces.forEachIndexed { i, f ->
                val d = f.map { m.vertices[it] dot m.normals[i] }
                assertTrue("face plana d$sides", d.max() - d.min() < 0.02f)
                assertTrue("normal para fora d$sides", (m.centroids[i] dot m.normals[i]) > 0f)
            }
        }
        assertEquals(setOf("1", "2"), DieMesh.of(2).labels.filter { it.isNotEmpty() }.toSet())
        assertEquals(10, DieMesh.of(100).faces.size)
    }

    @Test fun oppositeFacesSumLikeRealDice() {
        listOf(6, 8, 12, 20).forEach { sides ->
            val m = DieMesh.of(sides)
            m.faces.indices.forEach { i ->
                val opp = m.faces.indices.minBy { j -> (m.normals[j] + m.normals[i]).length }
                assertEquals("opostos no d$sides", sides + 1, m.labels[i].toInt() + m.labels[opp].toInt())
            }
        }
    }

    @Test fun resultFaceEndsFacingTheViewer() {
        listOf(2, 4, 6, 8, 10, 12, 20, 100).forEach { sides ->
            val m = DieMesh.of(sides)
            val values = if (sides == 100) listOf(7, 57, 100) else (1..sides).toList()
            values.forEach { v ->
                val face = m.faceFor(v)
                val rot = restingRotation(m, face, seed = v * 13)
                val front = m.faces.indices.maxBy { (rot * m.normals[it]).z }
                assertEquals("d$sides valor $v de frente", face, front)
                if (sides != 100) assertEquals("$v", m.labels[face])
            }
        }
        assertTrue(V3(0f, 0f, 1f).length == 1f)
    }
}
