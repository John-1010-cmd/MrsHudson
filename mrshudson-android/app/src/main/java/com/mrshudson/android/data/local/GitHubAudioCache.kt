package com.mrshudson.android.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub 音频缓存管理器
 * 用于从 GitHub 下载音频文件到本地缓存
 *
 * @param context 上下文
 */
class GitHubAudioCache(private val context: Context) {

    companion object {
        private const val TAG = "GitHubAudioCache"

        // 缓存目录
        private const val CACHE_DIR = "github_tts"

        // 最大缓存 100MB
        private const val MAX_CACHE_SIZE_MB = 100L
    }

    // 缓存目录
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * 检查是否有缓存的文件
     *
     * @param url 原始 URL（如 https://raw.githubusercontent.com/.../tts_xxx.mp3）
     * @return 缓存文件，如果不存在则返回 null
     */
    fun getCachedFile(url: String): File? {
        val fileName = getFileNameFromUrl(url)
        val cachedFile = File(cacheDir, fileName)
        return if (cachedFile.exists() && cachedFile.length() > 100) {
            Log.i(TAG, "找到缓存文件: ${cachedFile.absolutePath}, 大小: ${cachedFile.length()} bytes")
            cachedFile
        } else {
            null
        }
    }

    /**
     * 检查是否已缓存
     *
     * @param url 原始 URL
     * @return 是否已缓存
     */
    fun isCached(url: String): Boolean {
        return getCachedFile(url) != null
    }

    /**
     * 下载并缓存文件
     *
     * @param url GitHub Raw URL
     * @return 缓存的本地的文件，失败返回 null
     */
    suspend fun downloadAndCache(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUrl(url)
            val cachedFile = File(cacheDir, fileName)

            // 检查是否已有缓存
            if (cachedFile.exists() && cachedFile.length() > 100) {
                Log.i(TAG, "文件已缓存: ${cachedFile.absolutePath}")
                return@withContext cachedFile
            }

            // 清理缓存如果需要
            cleanCacheIfNeeded()

            Log.i(TAG, "开始下载: $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            try {
                val responseCode = connection.responseCode
                Log.i(TAG, "下载响应码: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val fileOutputStream = FileOutputStream(cachedFile)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }

                    fileOutputStream.flush()
                    fileOutputStream.close()
                    inputStream.close()

                    Log.i(TAG, "下载成功: ${cachedFile.absolutePath}, 大小: $totalBytesRead bytes")
                    cachedFile
                } else {
                    Log.e(TAG, "下载失败，响应码: $responseCode")
                    cachedFile.delete()
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载异常: ${e.message}", e)
            null
        }
    }

    /**
     * 获取缓存文件路径
     *
     * @param url 原始 URL
     * @return 本地文件路径
     */
    fun getLocalPath(url: String): String? {
        return getCachedFile(url)?.absolutePath
    }

    /**
     * 清理所有缓存
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "清理所有 GitHub TTS 缓存")
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }

    /**
     * 获取缓存大小（MB）
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() }?.div(1024 * 1024) ?: 0L
    }

    /**
     * 获取缓存目录路径
     */
    fun getCachePath(): String = cacheDir.absolutePath

    /**
     * 从 URL 中提取文件名
     */
    private fun getFileNameFromUrl(url: String): String {
        return try {
            val path = URL(url).path
            path.substringAfterLast("/")
        } catch (e: Exception) {
            // 如果解析失败，使用 URL 的 hashCode 作为文件名
            "tts_${url.hashCode()}.mp3"
        }
    }

    /**
     * 如果缓存超过最大限制，清理最旧的文件
     */
    private fun cleanCacheIfNeeded() {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            val maxSize = MAX_CACHE_SIZE_MB * 1024 * 1024

            if (totalSize > maxSize) {
                // 按最后修改时间排序，删除最旧的文件
                val sortedFiles = files.sortedBy { it.lastModified() }
                for (file in sortedFiles) {
                    if (totalSize <= maxSize * 0.8) break
                    val size = file.length()
                    if (file.delete()) {
                        totalSize -= size
                        Log.i(TAG, "清理缓存文件: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理缓存失败", e)
        }
    }
}
