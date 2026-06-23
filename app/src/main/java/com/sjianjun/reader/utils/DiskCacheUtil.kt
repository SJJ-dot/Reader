package com.sjianjun.reader.utils

import com.bumptech.glide.disklrucache.DiskLruCache
import com.sjianjun.coroutine.withIo
import java.io.File
import java.io.FileOutputStream

class DiskCacheUtil(private val root: File, maxSize: Long = 50 * 1024 * 1024) {
    private val diskCache by lazy {
        try {
            DiskLruCache.open(root, 1, 1, maxSize)
        } catch (e: Exception) {
            null
        }
    }


    suspend fun put(key: String, value: String) = withIo {
        diskCache?.edit(key.md5)?.run {
            try {
                FileOutputStream(getFile(0)).use {
                    it.write(value.zip())
                    it.flush()
                    commit()
                }
            } catch (e: Exception) {
                try {
                    abort()
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun get(key: String): String? = withIo {
        return@withIo try {
            diskCache?.get(key.md5)?.getFile(0)?.readBytes()?.unzip()?.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }


    suspend fun put(key: String, value: Any) = withIo {
        put(key, gson.toJson(value))
    }

    suspend fun delete() = withIo {
        diskCache?.delete()
    }

    suspend inline fun <reified T> getObj(key: String): T? = withIo {
        val str = get(key) ?: return@withIo null
        return@withIo gson.fromJson<T>(str)
    }
}
