package com.eokul.studio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eokul.studio.core.RosterParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface Screen {
    data object Home : Screen
    data class ClassDetail(val classId: String) : Screen
    data class Import(val classId: String?) : Screen
    data class Camera(val classId: String, val no: String) : Screen
    data object Review : Screen
    data object CaptureEditor : Screen
    data object Settings : Screen
    data object Batch : Screen
}

/** Çekilen (veya seçilen) tek bir fotoğrafın inceleme durumu. */
class CaptureState(
    val classId: String,
    val no: String,
    val source: Uri,
    val temp: File?,
    val fromSaved: Boolean
) {
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var session by mutableStateOf<EditSession?>(null)
    var saving by mutableStateOf(false)
}

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val store = RosterStore(File(ctx.filesDir, "roster.json"))
    private val storeMutex = Mutex()

    val classes = mutableStateListOf<SchoolClass>()
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var capture by mutableStateOf<CaptureState?>(null)
        private set
    var rootName by mutableStateOf<String?>(null)
        private set

    val settings: Settings get() = SettingsState.current
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var captureJob: Job? = null

    init {
        SettingsState.init(ctx)
        classes.addAll(store.load())
        refreshRootName()
    }

    private fun say(msg: String) {
        messages.tryEmit(msg)
    }

    // ------------------------------------------------------------------ gezinme
    fun navigate(s: Screen) {
        stack.add(s)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replace(s: Screen) {
        stack[stack.lastIndex] = s
    }

    fun handleBack() {
        when (screen) {
            Screen.Review -> discardCapture()
            Screen.CaptureEditor -> cancelCaptureEditor()
            else -> back()
        }
    }

    // ------------------------------------------------------------------ kalıcılık
    private fun persist() {
        val snap = classes.toList()
        viewModelScope.launch(Dispatchers.IO) {
            storeMutex.withLock { store.save(snap) }
        }
    }

    fun persistNow() {
        persist()
    }

    override fun onCleared() {
        store.save(classes.toList())
        super.onCleared()
    }

    fun classById(id: String?): SchoolClass? = classes.firstOrNull { it.id == id }

    private fun updateClass(id: String, fn: (SchoolClass) -> SchoolClass) {
        val i = classes.indexOfFirst { it.id == id }
        if (i < 0) return
        classes[i] = fn(classes[i])
        persist()
    }

    private fun updateStudent(classId: String, no: String, fn: (Student) -> Student) {
        updateClass(classId) { c ->
            c.copy(students = c.students.map { if (it.no == no) fn(it) else it })
        }
    }

    // ------------------------------------------------------------------ kayıt klasörü
    private fun refreshRootName() {
        val u = settings.rootUri?.let { Uri.parse(it) }
        rootName = if (u == null) null else try {
            DocumentFile.fromTreeUri(ctx, u)?.name
        } catch (e: Exception) {
            null
        }
    }

    fun hasRootAccess(): Boolean {
        val u = settings.rootUri?.let { Uri.parse(it) } ?: return false
        return ctx.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }
    }

    fun setRootFolder(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            say("Klasör izni alınamadı")
            return
        }
        SettingsState.update(settings.copy(rootUri = uri.toString()))
        refreshRootName()
        say("✓ Kayıt klasörü: ${rootName ?: "seçildi"}")
    }

    // ------------------------------------------------------------------ sınıflar
    private fun newId(): String =
        System.currentTimeMillis().toString(36) + (0..999).random().toString(36)

    private fun folderKey(name: String): String = RosterParser.sanitizeFileName(name).lowercase()

    /** Yeni sınıf ekler; ad boşsa veya aynı adlı sınıf varsa null döner. */
    fun addClass(name: String): String? {
        val n = RosterParser.normalizeSpaces(name)
        if (n.isEmpty()) return null
        if (classes.any { folderKey(it.name) == folderKey(n) }) return null
        val c = SchoolClass(newId(), n)
        val sorted = (classes.toList() + c).sortedWith { a, b -> compareNatural(a.name, b.name) }
        classes.clear()
        classes.addAll(sorted)
        persist()
        return c.id
    }

    /** Sınıfı yeniden adlandırır; kayıt klasöründeki klasörleri de (mümkünse) yeniden adlandırır. */
    fun renameClass(id: String, name: String): Boolean {
        val c = classById(id) ?: return false
        val n = RosterParser.normalizeSpaces(name)
        if (n.isEmpty()) return false
        if (classes.any { it.id != id && folderKey(it.name) == folderKey(n) }) {
            say("Bu adla başka bir sınıf var")
            return false
        }
        val oldDir = RosterParser.sanitizeFileName(c.name)
        val newDir = RosterParser.sanitizeFileName(n)
        val root = settings.rootUri?.let { Uri.parse(it) }
        if (root != null && oldDir != newDir) {
            viewModelScope.launch(Dispatchers.IO) {
                Output.renameDir(ctx, root, emptyList(), oldDir, newDir)
                Output.renameDir(ctx, root, listOf(EOKUL_DIR), oldDir, newDir)
            }
        }
        val sorted = classes.map { if (it.id == id) it.copy(name = n) else it }
            .sortedWith { a, b -> compareNatural(a.name, b.name) }
        classes.clear()
        classes.addAll(sorted)
        persist()
        return true
    }

    fun deleteClass(id: String) {
        val c = classById(id) ?: return
        classes.removeAll { it.id == id }
        persist()
        viewModelScope.launch(Dispatchers.IO) {
            Naming.privateDir(ctx, id).deleteRecursively()
        }
        say("“${c.name}” silindi (telefondaki fotoğraf dosyaları silinmedi)")
    }

    // ------------------------------------------------------------------ liste içe aktarma
    /** Sınıfa öğrenci ekler. [classId] null ise [newName] ile yeni sınıf açılır. Sonuç mesajı döner (hata: null). */
    fun importRoster(classId: String?, newName: String, rows: List<RosterParser.Row>, mode: MergeMode): String? {
        if (rows.isEmpty()) return null
        val id = classId ?: addClass(newName) ?: return null
        val cls = classById(id) ?: return null
        val r = mergeStudents(cls.students, rows, mode)
        updateClass(id) { it.copy(students = r.students) }
        val parts = ArrayList<String>()
        if (r.added > 0) parts.add("${r.added} eklendi")
        if (r.updated > 0) parts.add("${r.updated} güncellendi")
        if (r.removed > 0) parts.add("${r.removed} çıkarıldı")
        val msg = if (parts.isEmpty()) "Değişiklik yok" else parts.joinToString(", ")
        say("✓ $msg")
        return id
    }

    fun addStudent(classId: String, no: String, name: String): String? {
        val n = no.trim()
        val nm = RosterParser.normalizeSpaces(name)
        if (!n.matches(Regex("\\d{1,11}"))) return "Numara 1–11 haneli sayı olmalı"
        if (nm.length < 2) return "Ad soyad girin"
        val c = classById(classId) ?: return "Sınıf bulunamadı"
        if (c.students.any { it.no == n }) return "Bu numara zaten var"
        val list = (c.students + Student(n, nm)).sortedWith { a, b -> RosterParser.compareNo(a.no, b.no) }
        updateClass(classId) { it.copy(students = list) }
        return null
    }

    fun renameStudent(classId: String, no: String, name: String): String? {
        val nm = RosterParser.normalizeSpaces(name)
        if (nm.length < 2) return "Ad soyad girin"
        updateStudent(classId, no) { it.copy(name = nm) }
        return null
    }

    fun deleteStudent(classId: String, no: String) {
        updateClass(classId) { c -> c.copy(students = c.students.filter { it.no != no }) }
        viewModelScope.launch(Dispatchers.IO) { Naming.privatePhoto(ctx, classId, no).delete() }
    }

    /** Öğrencinin fotoğrafını (kayıt klasöründeki dosyalar dahil) siler; öğrenci listede kalır. */
    fun clearPhoto(classId: String, no: String) {
        val cls = classById(classId) ?: return
        val st = cls.students.firstOrNull { it.no == no } ?: return
        val root = settings.rootUri?.let { Uri.parse(it) }
        updateStudent(classId, no) {
            it.copy(captured = false, box = null, origUri = null, savedStem = null, faceCount = 0, updatedAt = 0L)
        }
        viewModelScope.launch(Dispatchers.IO) {
            Naming.privatePhoto(ctx, classId, no).delete()
            if (root != null) {
                val dir = RosterParser.sanitizeFileName(cls.name)
                val stem = st.savedStem
                if (stem != null) Output.deletePath(ctx, root, listOf(dir), "$stem.jpg")
                Output.deletePath(ctx, root, listOf(EOKUL_DIR, dir), "$no.jpg")
            }
            st.origUri?.let { Output.deleteUri(ctx, Uri.parse(it)) }
        }
        say("Fotoğraf silindi")
    }

    // ------------------------------------------------------------------ çekim akışı
    fun startSerial(classId: String) {
        val cls = classById(classId) ?: return
        val next = nextUncaptured(cls, null)
        if (next == null) {
            say("Tüm öğrencilerin fotoğrafı çekilmiş")
            return
        }
        openCamera(classId, next)
    }

    fun openCamera(classId: String, no: String) {
        if (settings.rootUri == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        navigate(Screen.Camera(classId, no))
    }

    /** Kamerada önceki / sonraki öğrenciye geç. */
    fun cameraMove(classId: String, no: String, delta: Int) {
        val list = classById(classId)?.students ?: return
        val i = list.indexOfFirst { it.no == no }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        replace(Screen.Camera(classId, list[j].no))
    }

    fun editSavedCrop(classId: String, no: String) {
        val st = classById(classId)?.students?.firstOrNull { it.no == no } ?: return
        val u = st.origUri
        if (u == null) {
            say("Orijinal fotoğraf saklanmamış; yeniden çekin")
            return
        }
        if (settings.rootUri == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        onPhotoReady(classId, no, Uri.parse(u), null, true, st.box)
    }

    fun onPhotoReady(
        classId: String, no: String, source: Uri, temp: File?,
        fromSaved: Boolean = false, savedBox: Box? = null
    ) {
        captureJob?.cancel()
        capture?.temp?.delete()
        val cap = CaptureState(classId, no, source, temp, fromSaved)
        capture = cap
        if (screen != Screen.Review) navigate(Screen.Review)
        val set = settings
        captureJob = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.Default) {
                    val bmp = ImageOps.decode(ctx, source)
                    val px = ImageOps.pixels(bmp)
                    val faces = ImageOps.detectFaces(bmp, px)
                    Triple(bmp, px, faces)
                }
                val bmp = r.first
                val px = r.second
                val faces = r.third
                val s = EditSession(no)
                s.bitmap = bmp
                s.pixels = px
                s.iw = bmp.width
                s.ih = bmp.height
                val box = if (savedBox != null) {
                    savedBox.fitInside(bmp.width, bmp.height)
                } else {
                    ImageOps.defaultBox(bmp.width, bmp.height, faces.firstOrNull(), set)
                }
                s.box = box
                s.startBox = box
                s.faces = faces
                s.faceFound = faces.isNotEmpty()
                s.loading = false
                cap.session = s
                cap.loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                cap.error = e.message ?: e.javaClass.simpleName
                cap.loading = false
            }
        }
    }

    fun discardCapture() {
        captureJob?.cancel()
        capture?.temp?.delete()
        capture = null
        if (screen == Screen.Review) back()
    }

    fun openCaptureEditor() {
        val s = capture?.session ?: return
        s.startBox = s.box
        navigate(Screen.CaptureEditor)
    }

    fun cancelCaptureEditor() {
        capture?.session?.let { it.box = it.startBox }
        if (screen == Screen.CaptureEditor) back()
    }

    fun applyCaptureEditor() {
        if (screen == Screen.CaptureEditor) back()
    }

    fun captureEditorPickFace(index: Int) {
        val s = capture?.session ?: return
        val f = s.faces.getOrNull(index) ?: return
        s.faceFound = true
        s.setBox(ImageOps.defaultBox(s.iw, s.ih, f, settings))
    }

    fun captureEditorResetToFace() {
        val s = capture?.session ?: return
        val f = s.faces.firstOrNull()
        s.setBox(ImageOps.defaultBox(s.iw, s.ih, f, settings))
    }

    // ------------------------------------------------------------------ kaydetme
    private class Saved(val origUri: String?, val stem: String)

    fun saveCapture() {
        val cap = capture ?: return
        val s = cap.session ?: return
        if (cap.saving) return
        val px = s.pixels ?: return
        val rootStr = settings.rootUri
        if (rootStr == null) {
            say("Önce kayıt klasörünü seçin")
            return
        }
        val root = Uri.parse(rootStr)
        val cls = classById(cap.classId) ?: return
        val st = cls.students.firstOrNull { it.no == cap.no } ?: return
        val set = settings
        val box = s.box.fitInside(s.iw, s.ih)
        val iw = s.iw
        val ih = s.ih
        val faceCount = s.faces.size
        cap.saving = true

        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { writeAll(root, cls, st, cap, px, iw, ih, box, set) }
                updateStudent(cap.classId, cap.no) {
                    it.copy(
                        captured = true, updatedAt = System.currentTimeMillis(), box = box,
                        origUri = saved.origUri, savedStem = saved.stem, faceCount = faceCount
                    )
                }
                cap.temp?.delete()
                capture = null
                afterSave(cap.classId, cap.no, set)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                cap.saving = false
                say("Kaydedilemedi: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun afterSave(classId: String, no: String, set: Settings) {
        if (screen == Screen.Review) back()
        val top = screen
        if (top is Screen.Camera) {
            val cls = classById(classId)
            val next = if (set.serialMode && cls != null) nextUncaptured(cls, no) else null
            if (next != null) {
                replace(Screen.Camera(classId, next))
            } else {
                back()
                say(if (set.serialMode) "🎉 Sınıftaki tüm fotoğraflar çekildi" else "✓ Kaydedildi")
            }
        } else {
            say("✓ Kaydedildi")
        }
    }

    private fun writeAll(
        root: Uri, cls: SchoolClass, st: Student, cap: CaptureState, px: IntArray,
        iw: Int, ih: Int, box: Box, set: Settings
    ): Saved {
        val stem = RosterParser.studentStem(st.no, st.name)
        val clsDir = RosterParser.sanitizeFileName(cls.name)
        val jpeg = ImageOps.render(ctx, px, iw, ih, box, set)

        Output.writePath(ctx, root, listOf(clsDir), "$stem.jpg", "image/jpeg", jpeg)
        if (set.eokulCopy) {
            Output.writePath(ctx, root, listOf(EOKUL_DIR, clsDir), "${st.no}.jpg", "image/jpeg", jpeg)
        }

        var origUri = st.origUri
        if (!cap.fromSaved && set.keepOriginals) {
            val bytes = ctx.contentResolver.openInputStream(cap.source)?.use { it.readBytes() }
                ?: throw IOException("Orijinal okunamadı")
            val mime = ctx.contentResolver.getType(cap.source) ?: "image/jpeg"
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
            val newUri = Output.writePath(ctx, root, listOf(clsDir, ORIGINALS_DIR), "$stem.$ext", mime, bytes)
            val old = st.origUri
            if (old != null && old != newUri.toString()) Output.deleteUri(ctx, Uri.parse(old))
            origUri = newUri.toString()
        }

        val oldStem = st.savedStem
        if (oldStem != null && oldStem != stem) {
            Output.deletePath(ctx, root, listOf(clsDir), "$oldStem.jpg")
        }

        val priv = Naming.privatePhoto(ctx, cls.id, st.no)
        priv.parentFile?.mkdirs()
        priv.writeBytes(jpeg)
        return Saved(origUri, stem)
    }
}
