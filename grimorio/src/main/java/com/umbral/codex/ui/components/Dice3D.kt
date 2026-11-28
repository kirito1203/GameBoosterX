package com.umbral.codex.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Mini motor 3D para os dados: vetores, rotações, poliedros reais (d4, d6, d8, d10, d12, d20 e
// moeda d2), iluminação por face e números desenhados no plano de cada face (perspectiva afim).
// ---------------------------------------------------------------------------------------------

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length get() = sqrt(x * x + y * y + z * z)
    fun norm(): V3 = length.let { if (it < 1e-6f) this else V3(x / it, y / it, z / it) }
    operator fun unaryMinus() = V3(-x, -y, -z)
}

/** Matriz de rotação 3×3 (linha a linha). */
class M3(private val m: FloatArray) {
    operator fun times(v: V3) = V3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    operator fun times(o: M3): M3 {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) r[i * 3 + j] = m[i * 3] * o.m[j] + m[i * 3 + 1] * o.m[3 + j] + m[i * 3 + 2] * o.m[6 + j]
        return M3(r)
    }

    companion object {
        val I = M3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        /** Rotação de [angle] radianos em torno de [axis] (Rodrigues). */
        fun axisAngle(axis: V3, angle: Float): M3 {
            val a = axis.norm(); val c = cos(angle); val s = sin(angle); val t = 1 - c
            return M3(floatArrayOf(
                t * a.x * a.x + c, t * a.x * a.y - s * a.z, t * a.x * a.z + s * a.y,
                t * a.x * a.y + s * a.z, t * a.y * a.y + c, t * a.y * a.z - s * a.x,
                t * a.x * a.z - s * a.y, t * a.y * a.z + s * a.x, t * a.z * a.z + c,
            ))
        }

        /** Menor rotação que leva a direção [from] até [to]. */
        fun align(from: V3, to: V3): M3 {
            val f = from.norm(); val t = to.norm()
            val d = (f dot t).coerceIn(-1f, 1f)
            if (d > 0.9999f) return I
            if (d < -0.9999f) {
                val ortho = if (abs(f.x) < 0.9f) V3(1f, 0f, 0f) else V3(0f, 1f, 0f)
                return axisAngle((f cross ortho).norm(), PI.toFloat())
            }
            return axisAngle((f cross t).norm(), kotlin.math.acos(d))
        }
    }
}

/** Poliedro de um dado: vértices (raio 1), faces ordenadas, rótulo e "cima" do número por face. */
class DieMesh(
    val sides: Int,
    val vertices: List<V3>,
    val faces: List<IntArray>,
    val labels: List<String>,
) {
    val normals: List<V3> = faces.map { f -> centroid(f).norm().let { c -> faceNormal(f).let { n -> if ((n dot c) < 0) -n else n } } }
    val centroids: List<V3> = faces.map { centroid(it) }
    val ups: List<V3> = faces.mapIndexed { i, f ->
        val c = centroids[i]
        // Triângulos e pipas: número aponta para o primeiro vértice; polígonos: para o meio da 1ª aresta.
        val target = if (f.size == 3 || sides == 10 || sides == 100) vertices[f[0]] else (vertices[f[0]] + vertices[f[1]]) * 0.5f
        val raw = target - c
        // Remove qualquer componente fora do plano da face.
        (raw - normals[i] * (raw dot normals[i])).norm()
    }

    /** Raio do círculo inscrito (para dimensionar o número). */
    val inradius: List<Float> = faces.mapIndexed { i, f ->
        val c = centroids[i]
        f.indices.minOf { k ->
            val a = vertices[f[k]]; val b = vertices[f[(k + 1) % f.size]]
            val ab = b - a
            ((c - a) cross ab).length / ab.length
        }
    }

    /** Metade do menor ângulo entre normais de faces distintas (em radianos). */
    val halfNeighborAngle: Float = run {
        var best = PI.toFloat()
        for (i in normals.indices) for (j in normals.indices) if (i != j) {
            val ang = kotlin.math.acos((normals[i] dot normals[j]).coerceIn(-1f, 1f))
            if (ang > 0.05f && ang < best) best = ang
        }
        (best / 2).coerceAtMost(0.6f)
    }

    private fun centroid(f: IntArray) = f.map { vertices[it] }.reduce { a, b -> a + b } * (1f / f.size)
    private fun faceNormal(f: IntArray): V3 {
        val a = vertices[f[0]]; val b = vertices[f[1]]; val c = vertices[f[2]]
        return ((b - a) cross (c - a)).norm()
    }

    /** Face cujo rótulo representa [value] (no d100, a face das dezenas). */
    fun faceFor(value: Int): Int {
        val key = when (sides) {
            100 -> "%02d".format(((value % 100) / 10) * 10)
            else -> value.toString()
        }
        return labels.indexOf(key).takeIf { it >= 0 } ?: 0
    }

    companion object {
        private val phi = ((1 + sqrt(5.0)) / 2).toFloat()
        private val cache = HashMap<Int, DieMesh>()

        fun of(sides: Int): DieMesh = cache.getOrPut(sides) {
            when (sides) {
                4 -> tetra(); 6 -> cube(); 8 -> octa(); 10 -> d10(sides, (1..10).map { it.toString() })
                12 -> dodeca(); 20 -> icosa(); 2 -> coin()
                100 -> d10(100, (0..9).map { "%02d".format(it * 10) })
                else -> icosa()
            }
        }

        private fun unit(vs: List<V3>): List<V3> { val r = vs.maxOf { it.length }; return vs.map { it * (1f / r) } }

        /** Faces a partir das normais: vértices mais extremos na direção de cada normal, em ordem angular. */
        private fun facesFromNormals(verts: List<V3>, normals: List<V3>): List<IntArray> = normals.map { n0 ->
            val n = n0.norm()
            val d = verts.map { it dot n }
            val mx = d.max(); val mn = d.min()
            val idx = verts.indices.filter { d[it] > mx - 0.02f * (mx - mn) }
            val c = idx.map { verts[it] }.reduce { a, b -> a + b } * (1f / idx.size)
            val ref = (verts[idx[0]] - c).norm(); val ref2 = n cross ref
            idx.sortedBy { val p = verts[it] - c; atan2(p dot ref2, p dot ref) }.toIntArray()
        }

        /** Rótulos com faces opostas somando N+1, como nos dados reais. */
        private fun oppositeLabels(normals: List<V3>): List<String> {
            val n = normals.size
            val labels = arrayOfNulls<String>(n)
            var next = 1
            for (i in 0 until n) {
                if (labels[i] != null) continue
                val opp = (0 until n).minByOrNull { j -> if (j == i || labels[j] != null) Float.MAX_VALUE else (normals[j] + normals[i]).length } ?: i
                labels[i] = next.toString(); labels[opp] = (n + 1 - next).toString(); next++
            }
            return labels.map { it ?: "?" }
        }

        private fun build(sides: Int, verts: List<V3>, normals: List<V3>, labels: List<String> = oppositeLabels(normals)) =
            DieMesh(sides, unit(verts), facesFromNormals(unit(verts), normals), labels)

        private fun tetra(): DieMesh {
            val v = listOf(V3(1f, 1f, 1f), V3(1f, -1f, -1f), V3(-1f, 1f, -1f), V3(-1f, -1f, 1f))
            return build(4, v, v.map { -it }, listOf("1", "2", "3", "4"))
        }

        private fun cube(): DieMesh {
            val v = buildList { for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) for (z in listOf(-1f, 1f)) add(V3(x, y, z)) }
            val n = listOf(V3(0f, 0f, 1f), V3(0f, 0f, -1f), V3(1f, 0f, 0f), V3(-1f, 0f, 0f), V3(0f, 1f, 0f), V3(0f, -1f, 0f))
            return build(6, v, n)
        }

        private fun octa(): DieMesh {
            val v = listOf(V3(1f, 0f, 0f), V3(-1f, 0f, 0f), V3(0f, 1f, 0f), V3(0f, -1f, 0f), V3(0f, 0f, 1f), V3(0f, 0f, -1f))
            val n = buildList { for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) for (z in listOf(-1f, 1f)) add(V3(x, y, z)) }
            return build(8, v, n)
        }

        private fun icosaVerts() = buildList {
            for (a in listOf(-1f, 1f)) for (b in listOf(-phi, phi)) { add(V3(0f, a, b)); add(V3(a, b, 0f)); add(V3(b, 0f, a)) }
        }

        /** Faces triangulares do icosaedro: trios de vértices todos à distância de uma aresta. */
        private fun icosaFaces(v: List<V3>): List<IntArray> {
            val edge = v.indices.flatMap { i -> (i + 1 until v.size).map { j -> (v[i] - v[j]).length } }.min()
            fun adj(i: Int, j: Int) = abs((v[i] - v[j]).length - edge) < edge * 0.01f
            val faces = mutableListOf<IntArray>()
            for (i in v.indices) for (j in i + 1 until v.size) for (k in j + 1 until v.size)
                if (adj(i, j) && adj(j, k) && adj(i, k)) faces += intArrayOf(i, j, k)
            return faces
        }

        private fun icosa(): DieMesh {
            val v = unit(icosaVerts())
            val faces = icosaFaces(v)
            val normals = faces.map { f -> (v[f[0]] + v[f[1]] + v[f[2]]).norm() }
            return DieMesh(20, v, faces, oppositeLabels(normals))
        }

        /** Dodecaedro como dual do icosaedro: vértices = centros das faces; faces = faces em volta de cada vértice. */
        private fun dodeca(): DieMesh {
            val iv = unit(icosaVerts())
            val iFaces = icosaFaces(iv)
            val dv = iFaces.map { f -> (iv[f[0]] + iv[f[1]] + iv[f[2]]).norm() }
            val faces = iv.indices.map { vi ->
                val n = iv[vi]
                val around = iFaces.indices.filter { vi in iFaces[it] }
                val ref = (dv[around[0]] - n * (dv[around[0]] dot n)).norm(); val ref2 = n cross ref
                around.sortedBy { val p = dv[it]; atan2(p dot ref2, p dot ref) }.toIntArray()
            }
            return DieMesh(12, dv, faces, oppositeLabels(iv))
        }

        /** Trapezoedro pentagonal (d10): pipas planas; a altura do ápice é ajustada numericamente. */
        private fun d10(sides: Int, labels: List<String>): DieMesh {
            val a = 0.11f
            fun ring(k: Int, upper: Boolean): V3 {
                val ang = (k * 72.0 + if (upper) 0.0 else 36.0) * PI / 180
                return V3(cos(ang).toFloat(), sin(ang).toFloat(), if (upper) a else -a)
            }
            // Ajusta h para que topo, u0, l0, u1 fiquem no mesmo plano.
            fun planarity(h: Float): Float {
                val top = V3(0f, 0f, h); val n = (ring(0, true) - top) cross (ring(1, true) - top)
                return n dot (ring(0, false) - top)
            }
            var lo = 0.2f; var hi = 3f
            repeat(60) { val mid = (lo + hi) / 2; if (planarity(lo) * planarity(mid) <= 0) hi = mid else lo = mid }
            val h = (lo + hi) / 2
            val verts = mutableListOf(V3(0f, 0f, h), V3(0f, 0f, -h))
            for (k in 0..4) verts += ring(k, true)   // índices 2..6
            for (k in 0..4) verts += ring(k, false)  // índices 7..11
            val faces = mutableListOf<IntArray>()
            for (k in 0..4) faces += intArrayOf(0, 2 + k, 7 + k, 2 + (k + 1) % 5)          // pipas de cima
            for (k in 0..4) faces += intArrayOf(1, 7 + (k + 1) % 5, 2 + (k + 1) % 5, 7 + k) // pipas de baixo
            // Escala vertical para proporção de dado real.
            val v = verts.map { V3(it.x, it.y, it.z * 0.85f) }
            val order = listOf(0, 5, 1, 6, 2, 7, 3, 8, 4, 9) // alterna cima/baixo para números espalhados
            val lab = MutableList(10) { "" }
            order.forEachIndexed { i, f -> lab[f] = labels[i] }
            return DieMesh(sides, unit(v), faces.map { f -> f }, lab)
        }

        /** Moeda (d2): prisma de 24 lados bem fino; números só nas faces. */
        private fun coin(): DieMesh {
            val n = 24; val t = 0.16f
            val verts = mutableListOf<V3>()
            for (k in 0 until n) { val ang = k * 2 * PI / n; verts += V3(cos(ang).toFloat(), sin(ang).toFloat(), t) }
            for (k in 0 until n) { val ang = k * 2 * PI / n; verts += V3(cos(ang).toFloat(), sin(ang).toFloat(), -t) }
            val faces = mutableListOf(IntArray(n) { it }, IntArray(n) { 2 * n - 1 - it })
            for (k in 0 until n) faces += intArrayOf(k, n + k, n + (k + 1) % n, (k + 1) % n)
            val labels = listOf("1", "2") + List(n) { "" }
            return DieMesh(2, unit(verts), faces, labels)
        }
    }
}

/** Orientação final: face do resultado de frente para a câmera, número em pé e leve inclinação 3D. */
fun restingRotation(mesh: DieMesh, face: Int, seed: Int): M3 {
    val toFront = M3.align(mesh.normals[face], V3(0f, 0f, 1f))
    val up = toFront * mesh.ups[face]
    val roll = M3.axisAngle(V3(0f, 0f, 1f), (PI / 2).toFloat() - atan2(up.y, up.x))
    val rnd = Random(seed)
    // Inclinação limitada pela metade do ângulo entre faces vizinhas: a face do resultado
    // continua sempre a mais de frente (senão o jogador leria o número errado).
    val maxTilt = mesh.halfNeighborAngle * 0.62f
    val tx = -(0.70f + rnd.nextFloat() * 0.30f) * maxTilt * 0.85f
    val ty = (rnd.nextFloat() - 0.5f) * maxTilt * 0.5f
    val tiltX = M3.axisAngle(V3(1f, 0f, 0f), tx)
    val tiltY = M3.axisAngle(V3(0f, 1f, 0f), ty)
    val twist = M3.axisAngle(V3(0f, 0f, 1f), (rnd.nextFloat() - 0.5f) * 0.35f)
    return tiltY * tiltX * twist * roll * toFront
}

private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    textSize = 100f
}
private val matrix = Matrix()
private val src = FloatArray(6)
private val dst = FloatArray(6)

/**
 * Desenha um dado 3D. [rot] orienta o poliedro; [center]/[radius] em pixels.
 * [valueLabel] substitui o rótulo da face do resultado (usado no d100: "57").
 */
fun DrawScope.drawDie3D(
    mesh: DieMesh,
    rot: M3,
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float = 1f,
    highlightFace: Int = -1,
    valueLabel: String? = null,
    edgeColor: Color = lerpColor(color, Color.White, 0.62f),
) {
    val persp = 4.5f
    fun project(p: V3): Offset { val k = persp / (persp - p.z); return Offset(center.x + p.x * k * radius, center.y - p.y * k * radius) }

    val rv = mesh.vertices.map { rot * it }
    val light = V3(-0.45f, 0.65f, 1f).norm()
    // Faces fundas e saturadas, arestas claras (visual de dado de resina)
    val dark = lerpColor(color, Color.Black, 0.86f)
    val bright = lerpColor(color, Color.Black, 0.40f)

    // Brilho de fundo (aura) e sombra no tabuleiro
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.28f * alpha), Color.Transparent), center, radius * 1.6f),
        radius * 1.6f, center,
    )

    val order = mesh.faces.indices
        .map { it to (rot * mesh.normals[it]) }
        .filter { it.second.z > 0.001f }
        .sortedBy { (i, _) -> (rot * mesh.centroids[i]).z }
    val path = Path()
    for ((i, n) in order) {
        val f = mesh.faces[i]
        path.reset()
        f.forEachIndexed { k, vi -> val p = project(rv[vi]); if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        path.close()
        val lambert = max(0f, n dot light)
        val spec = Math.pow(max(0f, n dot V3(-0.2f, 0.35f, 1f).norm()).toDouble(), 18.0).toFloat()
        var fill = lerpColor(dark, bright, 0.12f + 0.88f * Math.pow(lambert.toDouble(), 1.25).toFloat())
        fill = lerpColor(fill, lerpColor(color, Color.White, 0.35f), spec * 0.55f)
        if (i == highlightFace) fill = lerpColor(fill, bright, 0.12f)
        // Gradiente suave dentro da face dá volume
        val c2 = project(rot * mesh.centroids[i])
        drawPath(path, Brush.radialGradient(listOf(lerpColor(fill, color, 0.25f), fill), c2, radius * 0.9f), alpha = alpha)
        drawPath(path, edgeColor.copy(alpha = 0.75f * alpha), style = Stroke(width = radius * 0.022f + 0.6f, join = StrokeJoin.Round))

        // Número no plano da face
        val label = if (i == highlightFace && valueLabel != null) valueLabel else mesh.labels[i]
        if (label.isEmpty() || n.z < 0.18f) continue
        val facing = ((n.z - 0.18f) / 0.4f).coerceIn(0f, 1f)
        val digits = label.length
        val size = mesh.inradius[i] * (if (mesh.sides == 4) 0.95f else 1.12f) * (if (digits >= 2) 0.78f else 1f) * (if (digits >= 3) 0.75f else 1f)
        val c3 = rot * mesh.centroids[i]
        val up3 = rot * mesh.ups[i]
        val right3 = up3 cross n
        val q0 = project(c3); val q1 = project(c3 + right3 * size); val q2 = project(c3 + up3 * size)
        src[0] = 0f; src[1] = 0f; src[2] = 100f; src[3] = 0f; src[4] = 0f; src[5] = -100f
        dst[0] = q0.x; dst[1] = q0.y; dst[2] = q1.x; dst[3] = q1.y; dst[4] = q2.x; dst[5] = q2.y
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 3)) continue
        val textColor = Color(0xFFFFF4F0)
        numberPaint.color = textColor.copy(alpha = alpha * facing).toArgb()
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save(); nc.concat(matrix)
            val shown = if ((label == "6" || label == "9") && mesh.sides >= 8) "$label." else label
            nc.drawText(shown, 0f, 36f, numberPaint)
            nc.restore()
        }
    }
}

/** Interpolação suave usada na rolagem. */
fun easeOutCubic(t: Float): Float { val u = 1 - t.coerceIn(0f, 1f); return 1 - u * u * u }

/** Rotação durante a rolagem: gira em torno de um eixo aleatório e desacelera até a orientação final. */
fun tumbleRotation(final: M3, seed: Int, progress: Float): M3 {
    if (progress >= 1f) return final
    val rnd = Random(seed * 31 + 7)
    val axis = V3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).norm()
    val turns = 2.2f + rnd.nextFloat() * 2.2f
    val angle = (1 - easeOutCubic(progress)) * turns * 2 * PI.toFloat()
    return final * M3.axisAngle(axis, angle)
}
