package com.eokul.studio

import android.content.Context
import com.eokul.studio.core.RosterParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val ORIGINALS_DIR = "Orijinaller"
const val EOKUL_DIR = "E-Okul"

data class Student(
    val no: String,
    val name: String,
    val captured: Boolean = false,
    val updatedAt: Long = 0L,
    /** Son kırpma kutusu (çalışma görüntüsü koordinatlarında); orijinalden yeniden kırpmak için. */
    val box: Box? = null,
    /** Orijinal fotoğrafın SAF URI'si (Orijinaller klasöründe). */
    val origUri: String? = null,
    /** Son kayıtta kullanılan dosya adı (uzantısız), örn. "12345-Ayşe Yılmaz". */
    val savedStem: String? = null,
    val faceCount: Int = 0
)

data class SchoolClass(
    val id: String,
    val name: String,
    val students: List<Student> = emptyList()
) {
    val capturedCount: Int get() = students.count { it.captured }
}

enum class MergeMode { ADD, UPSERT, REPLACE }

data class MergeResult(val students: List<Student>, val added: Int, val updated: Int, val removed: Int)

/** Yapıştırılan satırları mevcut öğrenci listesiyle birleştirir; sonuç numaraya göre sıralanır. */
fun mergeStudents(existing: List<Student>, rows: List<RosterParser.Row>, mode: MergeMode): MergeResult {
    val byNo = LinkedHashMap<String, Student>()
    for (s in existing) byNo[s.no] = s
    var added = 0
    var updated = 0
    var removed = 0

    when (mode) {
        MergeMode.ADD -> {
            for (r in rows) {
                if (!byNo.containsKey(r.no)) {
                    byNo[r.no] = Student(r.no, r.name)
                    added++
                }
            }
        }
        MergeMode.UPSERT -> {
            for (r in rows) {
                val cur = byNo[r.no]
                if (cur == null) {
                    byNo[r.no] = Student(r.no, r.name)
                    added++
                } else if (cur.name != r.name) {
                    byNo[r.no] = cur.copy(name = r.name)
                    updated++
                }
            }
        }
        MergeMode.REPLACE -> {
            val keep = rows.map { it.no }.toHashSet()
            val replaced = LinkedHashMap<String, Student>()
            for (r in rows) {
                val cur = byNo[r.no]
                if (cur == null) {
                    replaced[r.no] = Student(r.no, r.name)
                    added++
                } else {
                    replaced[r.no] = cur.copy(name = r.name)
                    if (cur.name != r.name) updated++
                }
            }
            removed = byNo.keys.count { it !in keep }
            byNo.clear()
            byNo.putAll(replaced)
        }
    }
    val sorted = byNo.values.sortedWith { a, b -> RosterParser.compareNo(a.no, b.no) }
    return MergeResult(sorted, added, updated, removed)
}

/** Sıradaki fotoğrafı çekilmemiş öğrenci: önce [afterNo]'dan sonrakiler, yoksa baştan. */
fun nextUncaptured(cls: SchoolClass, afterNo: String?): String? {
    val list = cls.students
    val start = if (afterNo == null) 0 else list.indexOfFirst { it.no == afterNo } + 1
    for (i in start until list.size) if (!list[i].captured) return list[i].no
    for (i in 0 until minOf(start, list.size)) {
        if (!list[i].captured && list[i].no != afterNo) return list[i].no
    }
    return null
}

object Naming {
    private fun safe(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Uygulama içi 133x171 kopya: liste küçük resimleri ve yedek için. */
    fun privatePhoto(ctx: Context, classId: String, no: String): File =
        File(ctx.filesDir, "photos/${safe(classId)}/${safe(no)}.jpg")

    fun privateDir(ctx: Context, classId: String): File = File(ctx.filesDir, "photos/${safe(classId)}")
}

/** Sınıf ve öğrenci verisi (JSON, atomik yazım). */
class RosterStore(private val file: File) {

    fun load(): List<SchoolClass> = try {
        parse(file.readText())
    } catch (e: Exception) {
        emptyList()
    }

    fun parse(text: String): List<SchoolClass> {
        return try {
            val root = JSONObject(text)
            val arr = root.optJSONArray("classes") ?: return emptyList()
            val out = ArrayList<SchoolClass>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val sa = c.optJSONArray("students") ?: JSONArray()
                val students = ArrayList<Student>()
                for (j in 0 until sa.length()) {
                    val s = sa.optJSONObject(j) ?: continue
                    val b = s.optJSONArray("box")
                    students.add(
                        Student(
                            no = s.optString("no"),
                            name = s.optString("name"),
                            captured = s.optBoolean("captured"),
                            updatedAt = s.optLong("updatedAt"),
                            box = if (b != null && b.length() == 4) {
                                Box(b.getDouble(0), b.getDouble(1), b.getDouble(2), b.getDouble(3))
                            } else {
                                null
                            },
                            origUri = if (s.has("origUri") && !s.isNull("origUri")) s.getString("origUri") else null,
                            savedStem = if (s.has("savedStem") && !s.isNull("savedStem")) s.getString("savedStem") else null,
                            faceCount = s.optInt("faceCount")
                        )
                    )
                }
                out.add(SchoolClass(c.optString("id"), c.optString("name"), students))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(classes: List<SchoolClass>) {
        try {
            val arr = JSONArray()
            for (c in classes) {
                val sa = JSONArray()
                for (s in c.students) {
                    val o = JSONObject()
                        .put("no", s.no)
                        .put("name", s.name)
                        .put("captured", s.captured)
                        .put("updatedAt", s.updatedAt)
                        .put("faceCount", s.faceCount)
                    s.box?.let { o.put("box", JSONArray(listOf(it.x1, it.y1, it.x2, it.y2))) }
                    s.origUri?.let { o.put("origUri", it) }
                    s.savedStem?.let { o.put("savedStem", it) }
                    sa.put(o)
                }
                arr.put(JSONObject().put("id", c.id).put("name", c.name).put("students", sa))
            }
            val root = JSONObject().put("version", 1).put("classes", arr)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // kalıcılık başarısız olsa da uygulama çalışmaya devam eder
        }
    }
}
