package com.odettegd.nexus3d

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Choreographer
import android.view.Gravity
import android.view.SurfaceView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_OPEN_MODEL = 4102
        private const val REQUEST_SAVE_SCENE = 4103
        private const val REQUEST_OPEN_SCENE = 4104
        private const val REQUEST_EXPORT_GLB = 4105
        private const val MAX_MODEL_BYTES = 64 * 1024 * 1024
        private const val MAX_SCENE_BYTES = 4 * 1024 * 1024
        init { Utils.init() }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var statusText: TextView
    private var modelViewer: ModelViewer? = null
    private var activityResumed = false
    private var externalModelLoaded = false
    private var sceneDocument = SceneDocument.demo()
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            modelViewer?.render(frameTimeNanos)
            if (activityResumed) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 18, 26)
        window.navigationBarColor = Color.rgb(15, 18, 26)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 18, 26))
        }
        root.addView(TextView(this).apply {
            text = "NEXUS-3D"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(1))
        })
        root.addView(TextView(this).apply {
            text = "FILAMENT REAL-TIME EDITOR • GLB / NEXUS SCENE"
            textSize = 10f
            setTextColor(Color.rgb(158, 174, 196))
            setPadding(dp(16), 0, dp(16), dp(8))
        })

        val viewport = FrameLayout(this).apply { setBackgroundColor(Color.rgb(24, 30, 42)) }
        surfaceView = SurfaceView(this)
        viewport.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(viewport, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        statusText = TextView(this).apply {
            text = "Starting Filament renderer…"
            textSize = 11f
            setTextColor(Color.rgb(214, 222, 235))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            contentDescription = "3D viewport status"
        }
        root.addView(statusText)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(2), dp(5), dp(6))
        }
        controls.addView(buttonRow(
            button("Import GLB") { chooseModel() },
            button("Demo Cube") { loadDemoScene("Bundled Filament demo loaded.") },
            button("Reset View") {
                modelViewer?.resetToDefaultState()
                statusText.text = "Camera reset. Drag to orbit; pinch to zoom."
            }
        ))
        controls.addView(buttonRow(
            button("Add Cube") { mutateScene { add(PrimitiveKind.CUBE) } },
            button("Sphere") { mutateScene { add(PrimitiveKind.SPHERE) } },
            button("Plane") { mutateScene { add(PrimitiveKind.PLANE) } },
            button("Cylinder") { mutateScene { add(PrimitiveKind.CYLINDER) } }
        ))
        controls.addView(buttonRow(
            button("Next Object") { if (sceneDocument.objects.isNotEmpty()) {
                sceneDocument.select((sceneDocument.selectedIndex + 1) % sceneDocument.objects.size)
                updateSelectionStatus()
            } },
            button("Duplicate") { mutateScene { duplicateSelected() } },
            button("Delete") { mutateScene {
                deleteSelected()
                if (objects.isEmpty()) add(PrimitiveKind.CUBE)
            } },
            button("Rename") { renameSelected() }
        ))
        controls.addView(buttonRow(
            button("Move X") { mutateScene { translateSelected(0.25f, 0f, 0f) } },
            button("Rotate Y") { mutateScene { rotateSelected(0f, 15f, 0f) } },
            button("Scale +") { mutateScene { scaleSelected(1.1f) } },
            button("Scale −") { mutateScene { scaleSelected(0.9f) } }
        ))
        controls.addView(buttonRow(
            button("Undo") { restoreHistory(undoStack, redoStack, "Undo") },
            button("Redo") { restoreHistory(redoStack, undoStack, "Redo") },
            button("Save Scene") { createDocument(REQUEST_SAVE_SCENE, "nexus-scene.json", "application/json") },
            button("Open Scene") { chooseScene() },
            button("Export GLB") { createDocument(REQUEST_EXPORT_GLB, "nexus-scene.glb", "model/gltf-binary") }
        ))
        val controlScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(controls)
        }
        root.addView(controlScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(202)
        ))
        setContentView(root)

        val viewer = ModelViewer(surfaceView)
        modelViewer = viewer
        surfaceView.setOnTouchListener(viewer)
        loadDemoScene("Filament ready — bundled demo geometry visible.")
    }

    private fun buttonRow(vararg buttons: Button): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttons.forEach { row.addView(it, LinearLayout.LayoutParams(dp(104), dp(42)).apply {
            marginStart = dp(2); marginEnd = dp(2); bottomMargin = dp(2)
        }) }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setPadding(dp(3), 0, dp(3), 0)
            setOnClickListener { action() }
        }

    private fun mutateScene(action: SceneDocument.() -> Any?) {
        pushUndo()
        val result = sceneDocument.action()
        if (result == false) {
            if (undoStack.isNotEmpty()) undoStack.removeLast()
            toast("Select an object first.")
            return
        }
        redoStack.clear()
        externalModelLoaded = false
        renderEditorScene("Scene updated.")
    }

    private fun pushUndo() {
        undoStack.addLast(sceneDocument.toJson())
        while (undoStack.size > 50) undoStack.removeFirst()
    }

    private fun restoreHistory(from: ArrayDeque<String>, to: ArrayDeque<String>, label: String) {
        if (from.isEmpty()) { toast("Nothing to $label."); return }
        to.addLast(sceneDocument.toJson())
        sceneDocument.loadJson(from.removeLast())
        externalModelLoaded = false
        renderEditorScene("$label complete.")
    }

    private fun renameSelected() {
        val selected = sceneDocument.selected() ?: run { toast("Select an object first."); return }
        val input = EditText(this).apply { setText(selected.name); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("Rename object")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Rename") { _, _ ->
                pushUndo()
                if (sceneDocument.renameSelected(input.text.toString())) {
                    redoStack.clear()
                    externalModelLoaded = false
                    renderEditorScene("Object renamed.")
                } else toast("Object name cannot be empty.")
            }.show()
    }

    private fun updateSelectionStatus() {
        val item = sceneDocument.selected()
        statusText.text = if (item == null) "Scene is empty." else
            "Selected: ${item.name} • ${sceneDocument.selectedIndex + 1}/${sceneDocument.objects.size}"
    }

    private fun renderEditorScene(message: String = "Editor scene ready.") {
        try {
            val viewer = modelViewer ?: run { showError("Filament viewport is not initialized."); return }
            viewer.loadModelGlb(ByteBuffer.wrap(sceneDocument.toGlb()))
            if (viewer.asset == null) throw IOException("Filament did not create a scene asset.")
            viewer.transformToUnitCube()
            externalModelLoaded = false
            val selected = sceneDocument.selected()
            statusText.text = if (selected == null) message else
                "$message Selected: ${selected.name} • ${sceneDocument.objects.size} object(s)."
        } catch (error: Exception) {
            showError("Could not render editor scene: ${error.message ?: "unknown error"}")
            loadDemoScene("Editor scene failed; restored the bundled demo.")
        }
    }

    private fun chooseModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("model/gltf-binary", "application/octet-stream"))
        }
        try { startActivityForResult(intent, REQUEST_OPEN_MODEL) }
        catch (error: Exception) { showError("Could not open Android's file picker: ${error.message}") }
    }

    private fun chooseScene() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        try { startActivityForResult(intent, REQUEST_OPEN_SCENE) }
        catch (error: Exception) { showError("Could not open scene picker: ${error.message}") }
    }

    private fun createDocument(request: Int, title: String, mime: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, title)
        }
        try { startActivityForResult(intent, request) }
        catch (error: Exception) { showError("Could not create document: ${error.message}") }
    }

    @Deprecated("Document picker callback retained for broad Android compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_OPEN_MODEL -> importModel(uri)
            REQUEST_OPEN_SCENE -> openScene(uri)
            REQUEST_SAVE_SCENE -> writeDocument(uri, sceneDocument.toJson().toByteArray(Charsets.UTF_8), "Scene saved.")
            REQUEST_EXPORT_GLB -> try {
                writeDocument(uri, sceneDocument.toGlb(), "GLB scene exported.")
            } catch (error: Exception) { showError("GLB export failed: ${error.message}") }
        }
    }

    private fun importModel(uri: Uri) {
        val fileName = queryDisplayName(uri)
        if (fileName != null && !fileName.endsWith(".glb", ignoreCase = true)) {
            showError("This importer currently accepts binary glTF (.glb) only: $fileName")
            return
        }
        statusText.text = "Reading ${fileName ?: "model.glb"}…"
        ioExecutor.execute {
            try {
                val bytes = readLimited(uri, MAX_MODEL_BYTES)
                mainHandler.post {
                    val viewer = modelViewer ?: return@post
                    try {
                        viewer.loadModelGlb(ByteBuffer.wrap(bytes))
                        if (viewer.asset == null) throw IOException("Filament did not parse this GLB.")
                        viewer.transformToUnitCube()
                        externalModelLoaded = true
                        statusText.text = "Imported ${fileName ?: "GLB"} (${bytes.size / 1024} KiB). Editor object controls switch back to the editable scene."
                    } catch (error: Exception) {
                        loadDemoScene("Import failed; restored the bundled demo.")
                        toast("GLB load failed: ${error.message ?: "unknown error"}")
                    }
                }
            } catch (error: Exception) {
                mainHandler.post { showError(error.message ?: "Could not read selected GLB.") }
            }
        }
    }

    private fun openScene(uri: Uri) {
        ioExecutor.execute {
            try {
                val bytes = readLimited(uri, MAX_SCENE_BYTES)
                val json = bytes.toString(Charsets.UTF_8)
                mainHandler.post {
                    try {
                        pushUndo()
                        sceneDocument.loadJson(json)
                        redoStack.clear()
                        renderEditorScene("Nexus scene opened.")
                    } catch (error: Exception) {
                        showError("Invalid Nexus scene file: ${error.message}")
                    }
                }
            } catch (error: Exception) { mainHandler.post { showError(error.message ?: "Could not read scene file.") } }
        }
    }

    private fun writeDocument(uri: Uri, bytes: ByteArray, successMessage: String) {
        ioExecutor.execute {
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw IOException("Android could not open the selected destination.")
                mainHandler.post { statusText.text = successMessage }
            } catch (error: Exception) {
                mainHandler.post { showError("Could not write file: ${error.message}") }
            }
        }
    }

    private fun loadDemoScene(message: String) {
        try {
            val bytes = assets.open("demo-cube.glb").use { it.readBytes() }
            val viewer = modelViewer
            if (viewer == null) { statusText.text = "Filament viewport is not initialized yet."; return }
            viewer.loadModelGlb(ByteBuffer.wrap(bytes))
            if (viewer.asset == null) throw IOException("Bundled demo GLB could not be parsed.")
            viewer.transformToUnitCube()
            externalModelLoaded = false
            statusText.text = message
        } catch (error: Exception) { showError("Could not load built-in demo: ${error.message}") }
    }

    private fun readLimited(uri: Uri, limit: Int): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Android could not open the selected file.")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                total += count
                if (total > limit) throw IOException("File exceeds the ${limit / (1024 * 1024)} MiB safety limit.")
                output.write(chunk, 0, count)
            }
            if (total < 20) throw IOException("File is too small to be a valid scene/model.")
            return output.toByteArray()
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    } catch (_: Exception) { null }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun showError(message: String) {
        statusText.text = "ERROR: $message"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onPause() {
        activityResumed = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onPause()
    }

    override fun onDestroy() {
        activityResumed = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        ioExecutor.shutdownNow()
        super.onDestroy()
    }
}
