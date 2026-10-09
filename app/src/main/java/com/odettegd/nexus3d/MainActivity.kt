package com.odettegd.nexus3d

import android.app.Activity
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
import android.widget.LinearLayout
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
        private const val REQUEST_OPEN_GLB = 4102
        private const val MAX_MODEL_BYTES = 64 * 1024 * 1024

        // Initialize Filament's JNI/utility layer before constructing ModelViewer.
        init {
            Utils.init()
        }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var statusText: TextView
    private var modelViewer: ModelViewer? = null
    private var activityResumed = false

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
            setPadding(dp(16), dp(14), dp(16), dp(2))
        })

        root.addView(TextView(this).apply {
            text = "FILAMENT REAL-TIME VIEWPORT  •  GLB / GLTF"
            textSize = 11f
            setTextColor(Color.rgb(158, 174, 196))
            setPadding(dp(16), 0, dp(16), dp(12))
        })

        val viewport = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(24, 30, 42))
        }
        surfaceView = SurfaceView(this)
        viewport.addView(surfaceView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.addView(viewport, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        statusText = TextView(this).apply {
            text = "Starting Filament renderer…"
            textSize = 12f
            setTextColor(Color.rgb(214, 222, 235))
            setPadding(dp(16), dp(10), dp(16), dp(8))
            contentDescription = "3D viewport status"
        }
        root.addView(statusText)

        val rowOne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(2))
        }
        rowOne.addView(makeButton("Import GLB") { chooseGlb() }, buttonLayout())
        rowOne.addView(makeButton("Load Demo") { loadDemoScene("Demo cube loaded.") }, buttonLayout())
        root.addView(rowOne)

        val rowTwo = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(2), dp(10), dp(10))
        }
        rowTwo.addView(makeButton("Reset View") {
            val viewer = modelViewer
            if (viewer == null || viewer.asset == null) {
                loadDemoScene("Demo cube restored.")
            } else {
                viewer.resetToDefaultState()
                statusText.text = "View reset. Drag to orbit; pinch to zoom."
            }
        }, buttonLayout())
        rowTwo.addView(makeButton("Reload Demo") {
            loadDemoScene("Demo cube reloaded.")
        }, buttonLayout())
        root.addView(rowTwo)

        setContentView(root)

        val viewer = ModelViewer(surfaceView)
        modelViewer = viewer
        surfaceView.setOnTouchListener(viewer)

        // A bundled GLB means the viewport starts with actual Filament geometry, not a blank canvas.
        loadDemoScene("Filament ready — demo geometry is visible.")
    }

    private fun chooseGlb() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("model/gltf-binary", "application/octet-stream"))
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_GLB)
        } catch (error: Exception) {
            showError("Could not open Android's file picker: ${error.message}")
        }
    }

    @Deprecated("Platform document-picker callback kept to avoid adding another dependency.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_GLB || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val fileName = queryDisplayName(uri)

        if (fileName != null && !fileName.endsWith(".glb", ignoreCase = true)) {
            showError("Choose a binary glTF .glb file. Selected: $fileName")
            return
        }

        statusText.text = "Reading ${fileName ?: "model.glb"}…"
        ioExecutor.execute {
            try {
                val bytes = readLimitedModel(uri)
                mainHandler.post {
                    val viewer = modelViewer ?: return@post
                    try {
                        viewer.loadModelGlb(ByteBuffer.wrap(bytes))
                        if (viewer.asset == null) {
                            loadDemoScene("Filament could not parse that GLB; demo geometry restored.")
                            return@post
                        }
                        viewer.transformToUnitCube()
                        statusText.text = "Loaded ${fileName ?: "GLB"} (${bytes.size / 1024} KiB). Drag to orbit; pinch to zoom."
                    } catch (error: Exception) {
                        loadDemoScene("GLB load failed; demo geometry restored.")
                        Toast.makeText(
                            this,
                            "GLB load failed: ${error.message ?: "unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (error: Exception) {
                mainHandler.post { showError(error.message ?: "Could not read the selected GLB.") }
            }
        }
    }

    private fun loadDemoScene(message: String) {
        try {
            val bytes = assets.open("demo-cube.glb").use { it.readBytes() }
            val viewer = modelViewer
            if (viewer == null) {
                statusText.text = "Filament viewport is not initialized yet."
                return
            }
            viewer.loadModelGlb(ByteBuffer.wrap(bytes))
            if (viewer.asset == null) {
                statusText.text = "ERROR: bundled demo-cube.glb could not be parsed."
                return
            }
            viewer.transformToUnitCube()
            statusText.text = message
        } catch (error: Exception) {
            showError("Could not load the built-in demo: ${error.message}")
        }
    }

    private fun readLimitedModel(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Android could not open the selected file.")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(chunk)
                if (count < 0) break
                total += count
                if (total > MAX_MODEL_BYTES) {
                    throw IOException("GLB exceeds the 64 MiB import safety limit.")
                }
                output.write(chunk, 0, count)
            }
            if (total < 20) throw IOException("This file is too small to be a valid GLB.")
            return output.toByteArray()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun buttonLayout(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginStart = dp(4)
            marginEnd = dp(4)
        }

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
        // ModelViewer owns a detach listener and releases resources when SurfaceView detaches.
        // Do not call destroy() here too: that can double-destroy Filament resources.
        super.onDestroy()
    }
}
