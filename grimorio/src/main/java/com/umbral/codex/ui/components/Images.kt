package com.umbral.codex.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umbral.codex.core.Ids
import com.umbral.codex.data.CodexRepository
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Imagens do usuário: reduzidas para no máximo 1024 px e salvas como JPEG no armazenamento do app. */
object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun get(id: String): ImageBitmap? = cache.get(id)

    suspend fun load(repo: CodexRepository, id: String): ImageBitmap? = get(id) ?: withContext(Dispatchers.IO) {
        val f = repo.imageFile(id)
        if (!f.exists()) return@withContext null
        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()?.also { cache.put(id, it) }
    }

    /** Lê a imagem escolhida, reduz, comprime e devolve o id salvo (ou null se falhar). */
    suspend fun import(context: Context, repo: CodexRepository, uri: Uri, maxSize: Int = 1024): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            val scale = maxSize.toFloat() / max(decoded.width, decoded.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val id = "img-" + Ids.new()
            repo.saveImage(id, out.toByteArray())
            cache.put(id, bmp.asImageBitmap())
            id
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun rememberImage(id: String?): ImageBitmap? {
    val repo = LocalRepository.current
    var img by remember(id) { mutableStateOf(id?.let { ImageCache.get(it) }) }
    LaunchedEffect(id) { img = if (id == null) null else ImageCache.load(repo, id) }
    return img
}

/** Abre o seletor de fotos do sistema e devolve o id da imagem importada. */
@Composable
fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { onPicked(ImageCache.import(context, repo, uri)) }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Retrato com moldura; sem imagem mostra a inicial sobre um gradiente. */
@Composable
fun Portrait(imageId: String?, name: String, modifier: Modifier, shape: Shape = CircleShape) {
    val c = Codex.colors
    val img = rememberImage(imageId)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(c.accentDeep, c.abyss)))
            .border(1.5.dp, c.gold.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(img, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium, color = c.gold, fontFamily = TitleFont,
            )
        }
    }
}
