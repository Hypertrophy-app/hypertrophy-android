package app.hypertrophy.core.data.assets

import android.content.Context
import app.hypertrophy.core.data.remote.HypertrophyApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Two-tier `.glb` cache. Models live in `filesDir/models/` so a first view
 * streams from R2 and every later gym session loads entirely from disk.
 */
@Singleton
class ExerciseModelCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
) {
    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    fun cachedModel(exerciseId: String): File? {
        val file = File(modelsDir, "$exerciseId.glb")
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    /**
     * Returns a local model file, streaming and caching it on the first call.
     * Returns null when offline and no cached copy exists.
     */
    suspend fun resolveModel(exerciseId: String): File? = withContext(Dispatchers.IO) {
        cachedModel(exerciseId)?.let { return@withContext it }

        val remote = "${HypertrophyApi.BASE_URL}/v1/assets/models/$exerciseId.glb"
        val response = runCatching { httpClient.get(remote) }.getOrNull() ?: return@withContext null
        if (!response.status.isSuccess()) return@withContext null

        val temp = File(modelsDir, "$exerciseId.glb.part")
        runCatching {
            temp.outputStream().use { out ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
            val target = File(modelsDir, "$exerciseId.glb")
            if (target.exists()) target.delete()
            temp.renameTo(target)
            target.takeIf { it.exists() && it.length() > 0 }
        }.getOrElse {
            temp.delete()
            null
        }
    }
}
