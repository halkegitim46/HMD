package com.eokul.studio

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * Çıktı yazıcı.
 *  - Sınıf akışı: ana klasör (SAF ağacı) altında iç içe klasörler (Sınıf/, Sınıf/Orijinaller/, E-Okul/Sınıf/).
 *  - Toplu kırpma akışı: seçilen klasörün alt klasörüne veya Pictures/ altına (MediaStore).
 * Aynı adlı dosya varsa üzerine yazılır; böylece dosya adları (öğrenci numarası) bozulmaz.
 */
object Output {

    // ------------------------------------------------------------------ iç içe klasör yolu (sınıf akışı)

    private fun dirFor(ctx: Context, tree: Uri, segments: List<String>, create: Boolean): DocumentFile? {
        var cur = DocumentFile.fromTreeUri(ctx, tree) ?: throw IOException("Ana klasöre erişilemiyor")
        for (seg in segments) {
            val found = cur.findFile(seg)
            cur = if (found != null && found.isDirectory) {
                found
            } else if (create) {
                cur.createDirectory(seg) ?: throw IOException("Klasör oluşturulamadı: $seg")
            } else {
                return null
            }
        }
        return cur
    }

    /** Dosyayı yazar (varsa üzerine) ve oluşturulan dosyanın URI'sini döndürür. */
    fun writePath(
        ctx: Context, tree: Uri, segments: List<String>, name: String, mime: String, bytes: ByteArray
    ): Uri {
        val dir = dirFor(ctx, tree, segments, true) ?: throw IOException("Klasör bulunamadı")
        dir.findFile(name)?.delete()
        val file = dir.createFile(mime, name) ?: throw IOException("Dosya oluşturulamadı: $name")
        val out = ctx.contentResolver.openOutputStream(file.uri, "wt")
            ?: throw IOException("Dosya yazılamadı: $name")
        out.use { it.write(bytes) }
        return file.uri
    }

    /** Varsa siler; yoksa sessizce geçer. */
    fun deletePath(ctx: Context, tree: Uri, segments: List<String>, name: String) {
        try {
            dirFor(ctx, tree, segments, false)?.findFile(name)?.delete()
        } catch (e: Exception) {
            // silinemeyen dosya kritik değil
        }
    }

    /** Klasörü yeniden adlandırır (en iyi çaba; hedef adla klasör varsa dokunmaz). */
    fun renameDir(ctx: Context, tree: Uri, parent: List<String>, oldName: String, newName: String) {
        try {
            val dir = dirFor(ctx, tree, parent, false) ?: return
            val old = dir.findFile(oldName) ?: return
            if (dir.findFile(newName) != null) return
            old.renameTo(newName)
        } catch (e: Exception) {
            // yoksay
        }
    }

    /** Klasördeki dosyayı yeniden adlandırır; başarılıysa true. */
    fun renameFile(ctx: Context, tree: Uri, segments: List<String>, oldName: String, newName: String): Boolean {
        return try {
            val dir = dirFor(ctx, tree, segments, false) ?: return false
            val f = dir.findFile(oldName) ?: return false
            dir.findFile(newName)?.delete()
            f.renameTo(newName)
        } catch (e: Exception) {
            false
        }
    }

    /** Tek dosyayı yeni uzantısız ad ile yeniden adlandırır (uzantı korunur); yeni URI veya null. */
    fun renameUri(ctx: Context, uri: Uri, newStem: String): Uri? {
        return try {
            val df = DocumentFile.fromSingleUri(ctx, uri) ?: return null
            val ext = (df.name ?: "").substringAfterLast('.', "jpg")
            if (df.renameTo("$newStem.$ext")) df.uri else null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteUri(ctx: Context, uri: Uri) {
        try {
            DocumentFile.fromSingleUri(ctx, uri)?.delete()
        } catch (e: Exception) {
            // yoksay
        }
    }

    // ------------------------------------------------------------------ toplu kırpma akışı

    fun write(ctx: Context, tree: Uri?, dirName: String, name: String, bytes: ByteArray) {
        if (tree != null) writePath(ctx, tree, listOf(dirName), name, "image/jpeg", bytes)
        else writeMedia(ctx, dirName, name, bytes)
    }

    fun locationLabel(tree: Uri?, rootName: String?, dirName: String): String =
        if (tree != null) "${rootName ?: "klasör"}/$dirName" else "Pictures/$dirName"

    private fun writeMedia(ctx: Context, dirName: String, name: String, bytes: ByteArray) {
        val resolver = ctx.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val relPath = "${Environment.DIRECTORY_PICTURES}/$dirName"

        var existing: Uri? = null
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "$relPath%"),
            null
        )?.use { c ->
            existing = if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }

        val ex = existing
        if (ex != null) {
            try {
                val os = resolver.openOutputStream(ex, "wt")
                if (os != null) {
                    os.use { it.write(bytes) }
                    return
                }
            } catch (e: Exception) {
                // başka uygulamaya ait dosya olabilir: yeni dosya olarak eklenir
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Dosya oluşturulamadı: $name")
        try {
            val os = resolver.openOutputStream(uri, "wt") ?: throw IOException("Dosya yazılamadı: $name")
            os.use { it.write(bytes) }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }
}
