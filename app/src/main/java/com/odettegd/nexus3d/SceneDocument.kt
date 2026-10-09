package com.odettegd.nexus3d

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class PrimitiveKind { CUBE, SPHERE, PLANE, CYLINDER }

data class SceneObject(
    val id: String,
    var name: String,
    val primitive: PrimitiveKind,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var rx: Float = 0f,
    var ry: Float = 0f,
    var rz: Float = 0f,
    var sx: Float = 1f,
    var sy: Float = 1f,
    var sz: Float = 1f
)

/**
 * Serializable editor-owned scene. Geometry is exported as a standards-compliant GLB and
 * rendered by the real Filament glTF loader; this is not a preview/canvas renderer.
 */
class SceneDocument {
    val objects: MutableList<SceneObject> = mutableListOf()
    var selectedIndex: Int = -1
        private set

    fun add(kind: PrimitiveKind): SceneObject {
        val item = SceneObject(
            id = "node-${nextId++}",
            name = kind.name.lowercase().replaceFirstChar { it.uppercase() },
            primitive = kind
        )
        objects += item
        selectedIndex = objects.lastIndex
        return item
    }

    fun selected(): SceneObject? = objects.getOrNull(selectedIndex)

    fun select(index: Int): Boolean {
        if (index !in objects.indices) return false
        selectedIndex = index
        return true
    }

    fun duplicateSelected(): SceneObject? {
        val source = selected() ?: return null
        val copy = source.copy(id = "node-${nextId++}", name = "${source.name} Copy", x = source.x + 0.25f)
        objects.add(selectedIndex + 1, copy)
        selectedIndex += 1
        return copy
    }

    fun deleteSelected(): Boolean {
        if (selectedIndex !in objects.indices) return false
        objects.removeAt(selectedIndex)
        selectedIndex = if (objects.isEmpty()) -1 else selectedIndex.coerceAtMost(objects.lastIndex)
        return true
    }

    fun renameSelected(name: String): Boolean {
        val item = selected() ?: return false
        val safe = name.trim().take(80)
        if (safe.isEmpty()) return false
        item.name = safe
        return true
    }

    fun translateSelected(dx: Float, dy: Float, dz: Float): Boolean {
        val item = selected() ?: return false
        item.x += dx; item.y += dy; item.z += dz
        return true
    }

    fun rotateSelected(dx: Float, dy: Float, dz: Float): Boolean {
        val item = selected() ?: return false
        item.rx = (item.rx + dx) % 360f
        item.ry = (item.ry + dy) % 360f
        item.rz = (item.rz + dz) % 360f
        return true
    }

    fun scaleSelected(factor: Float): Boolean {
        val item = selected() ?: return false
        if (!factor.isFinite() || factor <= 0f) return false
        item.sx = (item.sx * factor).coerceIn(0.01f, 100f)
        item.sy = (item.sy * factor).coerceIn(0.01f, 100f)
        item.sz = (item.sz * factor).coerceIn(0.01f, 100f)
        return true
    }

    fun toJson(): String {
        val root = JSONObject().put("format", "nexus-scene").put("version", 1)
        val nodes = JSONArray()
        objects.forEach { o ->
            nodes.put(JSONObject()
                .put("id", o.id).put("name", o.name).put("primitive", o.primitive.name)
                .put("translation", JSONArray().put(o.x).put(o.y).put(o.z))
                .put("rotationDegrees", JSONArray().put(o.rx).put(o.ry).put(o.rz))
                .put("scale", JSONArray().put(o.sx).put(o.sy).put(o.sz)))
        }
        root.put("nodes", nodes).put("selectedIndex", selectedIndex)
        return root.toString(2)
    }

    fun loadJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "nexus-scene") { "Not a Nexus scene file." }
        require(root.optInt("version") == 1) { "Unsupported Nexus scene version." }
        val restored = mutableListOf<SceneObject>()
        val nodes = root.getJSONArray("nodes")
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            val t = n.getJSONArray("translation")
            val r = n.getJSONArray("rotationDegrees")
            val s = n.getJSONArray("scale")
            val kind = PrimitiveKind.valueOf(n.getString("primitive"))
            restored += SceneObject(
                id = n.optString("id", "node-${i + 1}"),
                name = n.optString("name", kind.name),
                primitive = kind,
                x = t.getDouble(0).toFloat(), y = t.getDouble(1).toFloat(), z = t.getDouble(2).toFloat(),
                rx = r.getDouble(0).toFloat(), ry = r.getDouble(1).toFloat(), rz = r.getDouble(2).toFloat(),
                sx = s.getDouble(0).toFloat(), sy = s.getDouble(1).toFloat(), sz = s.getDouble(2).toFloat()
            )
        }
        require(restored.size <= 500) { "Scene exceeds the 500-object safety limit." }
        require(restored.all { it.name.isNotBlank() && listOf(it.x,it.y,it.z,it.rx,it.ry,it.rz,it.sx,it.sy,it.sz).all(Float::isFinite) }) {
            "Scene contains invalid object properties."
        }
        objects.clear()
        objects.addAll(restored)
        selectedIndex = root.optInt("selectedIndex", if (objects.isEmpty()) -1 else 0)
            .takeIf { it in objects.indices } ?: if (objects.isEmpty()) -1 else 0
        nextId = max(nextId, objects.size + 1)
    }

    fun toGlb(): ByteArray {
        require(objects.isNotEmpty()) { "A scene must contain at least one object." }
        require(objects.size <= 500) { "Scene exceeds the 500-object safety limit." }
        val binary = ByteArrayOutputStream()
        val views = JSONArray()
        val accessors = JSONArray()
        val meshes = JSONArray()
        val nodes = JSONArray()

        objects.forEachIndexed { index, item ->
            val geometry = geometry(item.primitive)
            align4(binary)
            val positionOffset = binary.size()
            binary.write(floatBytes(geometry.positions))
            views.put(bufferView(positionOffset, binary.size() - positionOffset, 34962))
            val positionAccessor = accessors.length()
            accessors.put(JSONObject().put("bufferView", views.length() - 1).put("componentType", 5126)
                .put("count", geometry.positions.size / 3).put("type", "VEC3")
                .put("min", JSONArray().put(geometry.minX).put(geometry.minY).put(geometry.minZ))
                .put("max", JSONArray().put(geometry.maxX).put(geometry.maxY).put(geometry.maxZ)))

            align4(binary)
            val normalOffset = binary.size()
            binary.write(floatBytes(geometry.normals))
            views.put(bufferView(normalOffset, binary.size() - normalOffset, 34962))
            val normalAccessor = accessors.length()
            accessors.put(JSONObject().put("bufferView", views.length() - 1).put("componentType", 5126)
                .put("count", geometry.normals.size / 3).put("type", "VEC3"))

            align4(binary)
            val indexOffset = binary.size()
            binary.write(shortBytes(geometry.indices))
            views.put(bufferView(indexOffset, binary.size() - indexOffset, 34963))
            val indexAccessor = accessors.length()
            accessors.put(JSONObject().put("bufferView", views.length() - 1).put("componentType", 5123)
                .put("count", geometry.indices.size).put("type", "SCALAR")
                .put("min", JSONArray().put(0)).put("max", JSONArray().put(geometry.indices.maxOrNull() ?: 0)))

            val primitive = JSONObject().put("attributes", JSONObject()
                .put("POSITION", positionAccessor).put("NORMAL", normalAccessor))
                .put("indices", indexAccessor).put("material", 0).put("mode", 4)
            meshes.put(JSONObject().put("name", item.name).put("primitives", JSONArray().put(primitive)))
            nodes.put(JSONObject().put("name", item.name).put("mesh", index)
                .put("translation", JSONArray().put(item.x).put(item.y).put(item.z))
                .put("rotation", quaternion(item.rx, item.ry, item.rz))
                .put("scale", JSONArray().put(item.sx).put(item.sy).put(item.sz)))
        }

        val root = JSONObject()
            .put("asset", JSONObject().put("version", "2.0").put("generator", "Nexus-3D SceneDocument"))
            .put("scene", 0)
            .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().also { a -> objects.indices.forEach(a::put) })))
            .put("nodes", nodes)
            .put("meshes", meshes)
            .put("materials", JSONArray().put(JSONObject().put("name", "Nexus Default PBR")
                .put("pbrMetallicRoughness", JSONObject()
                    .put("baseColorFactor", JSONArray().put(0.98).put(0.28).put(0.07).put(1.0))
                    .put("metallicFactor", 0.08).put("roughnessFactor", 0.38))))
            .put("accessors", accessors)
            .put("bufferViews", views)
            .put("buffers", JSONArray().put(JSONObject().put("byteLength", binary.size())))

        val jsonBytes = root.toString().toByteArray(Charsets.UTF_8).let { raw ->
            val padded = ByteArray((raw.size + 3) and 3.inv()) { 0x20 }
            raw.copyInto(padded)
            padded
        }
        val binBytes = binary.toByteArray().let { raw ->
            val padded = ByteArray((raw.size + 3) and 3.inv())
            raw.copyInto(padded)
            padded
        }
        val total = 12 + 8 + jsonBytes.size + 8 + binBytes.size
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67); putInt(2); putInt(total)
            putInt(jsonBytes.size); putInt(0x4E4F534A); put(jsonBytes)
            putInt(binBytes.size); putInt(0x004E4942); put(binBytes)
        }.array()
    }

    private fun bufferView(offset: Int, length: Int, target: Int) =
        JSONObject().put("buffer", 0).put("byteOffset", offset).put("byteLength", length).put("target", target)

    private fun align4(out: ByteArrayOutputStream) { while (out.size() % 4 != 0) out.write(0) }
    private fun floatBytes(values: FloatArray): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach(::putFloat) }.array()
    private fun shortBytes(values: IntArray): ByteArray =
        ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putShort(it.toShort()) } }.array()

    private fun quaternion(rx: Float, ry: Float, rz: Float): JSONArray {
        val x = Math.toRadians(rx.toDouble()) / 2
        val y = Math.toRadians(ry.toDouble()) / 2
        val z = Math.toRadians(rz.toDouble()) / 2
        val cx = cos(x); val sx = sin(x); val cy = cos(y); val sy = sin(y); val cz = cos(z); val sz = sin(z)
        return JSONArray().put(sx * cy * cz - cx * sy * sz).put(cx * sy * cz + sx * cy * sz)
            .put(cx * cy * sz - sx * sy * cz).put(cx * cy * cz + sx * sy * sz)
    }

    private data class Geometry(
        val positions: FloatArray, val normals: FloatArray, val indices: IntArray,
        val minX: Float, val minY: Float, val minZ: Float, val maxX: Float, val maxY: Float, val maxZ: Float
    )

    private fun geometry(kind: PrimitiveKind): Geometry {
        val p = mutableListOf<Float>(); val n = mutableListOf<Float>(); val idx = mutableListOf<Int>()
        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            p += x; p += y; p += z; n += nx; n += ny; n += nz
        }
        fun tri(a: Int, b: Int, c: Int) { idx += a; idx += b; idx += c }
        when (kind) {
            PrimitiveKind.CUBE -> {
                val faces = listOf(
                    Triple(floatArrayOf(0f,0f,1f), arrayOf(floatArrayOf(-.5f,-.5f,.5f),floatArrayOf(.5f,-.5f,.5f),floatArrayOf(.5f,.5f,.5f),floatArrayOf(-.5f,.5f,.5f)), 1),
                    Triple(floatArrayOf(0f,0f,-1f), arrayOf(floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(-.5f,.5f,-.5f),floatArrayOf(.5f,.5f,-.5f)), 1),
                    Triple(floatArrayOf(1f,0f,0f), arrayOf(floatArrayOf(.5f,-.5f,.5f),floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(.5f,.5f,-.5f),floatArrayOf(.5f,.5f,.5f)), 1),
                    Triple(floatArrayOf(-1f,0f,0f), arrayOf(floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(-.5f,-.5f,.5f),floatArrayOf(-.5f,.5f,.5f),floatArrayOf(-.5f,.5f,-.5f)), 1),
                    Triple(floatArrayOf(0f,1f,0f), arrayOf(floatArrayOf(-.5f,.5f,.5f),floatArrayOf(.5f,.5f,.5f),floatArrayOf(.5f,.5f,-.5f),floatArrayOf(-.5f,.5f,-.5f)), 1),
                    Triple(floatArrayOf(0f,-1f,0f), arrayOf(floatArrayOf(-.5f,-.5f,-.5f),floatArrayOf(.5f,-.5f,-.5f),floatArrayOf(.5f,-.5f,.5f),floatArrayOf(-.5f,-.5f,.5f)), 1)
                )
                faces.forEach { (normal, corners, _) ->
                    val base = p.size / 3
                    corners.forEach { vertex(it[0],it[1],it[2],normal[0],normal[1],normal[2]) }
                    tri(base,base+1,base+2); tri(base,base+2,base+3)
                }
            }
            PrimitiveKind.PLANE -> {
                vertex(-.5f,0f,-.5f,0f,1f,0f); vertex(.5f,0f,-.5f,0f,1f,0f)
                vertex(.5f,0f,.5f,0f,1f,0f); vertex(-.5f,0f,.5f,0f,1f,0f)
                tri(0,2,1); tri(0,3,2)
            }
            PrimitiveKind.SPHERE -> {
                val lat = 12; val lon = 20
                for (a in 0..lat) {
                    val theta = Math.PI * a / lat
                    for (b in 0..lon) {
                        val phi = 2.0 * Math.PI * b / lon
                        val x = (-cos(phi) * sin(theta)).toFloat()
                        val y = cos(theta).toFloat()
                        val z = (sin(phi) * sin(theta)).toFloat()
                        vertex(x*.5f,y*.5f,z*.5f,x,y,z)
                    }
                }
                for (a in 0 until lat) for (b in 0 until lon) {
                    val k = a * (lon + 1) + b
                    tri(k,k+lon+1,k+1); tri(k+1,k+lon+1,k+lon+2)
                }
            }
            PrimitiveKind.CYLINDER -> {
                val segments = 24
                for (i in 0..segments) {
                    val angle = 2.0 * Math.PI * i / segments
                    val x = cos(angle).toFloat(); val z = sin(angle).toFloat()
                    vertex(x*.5f,-.5f,z*.5f,x,0f,z); vertex(x*.5f,.5f,z*.5f,x,0f,z)
                }
                for (i in 0 until segments) {
                    val a = i*2; val b = a+1; val c = a+2; val d = a+3
                    tri(a,b,c); tri(c,b,d)
                }
                val bottomCenter = p.size / 3; vertex(0f,-.5f,0f,0f,-1f,0f)
                val bottomRing = p.size / 3
                for (i in 0..segments) { val angle=2.0*Math.PI*i/segments; vertex((cos(angle)*.5).toFloat(),-.5f,(sin(angle)*.5).toFloat(),0f,-1f,0f) }
                val topCenter = p.size / 3; vertex(0f,.5f,0f,0f,1f,0f)
                val topRing = p.size / 3
                for (i in 0..segments) { val angle=2.0*Math.PI*i/segments; vertex((cos(angle)*.5).toFloat(),.5f,(sin(angle)*.5).toFloat(),0f,1f,0f) }
                for (i in 0 until segments) {
                    tri(bottomCenter,bottomRing+i+1,bottomRing+i)
                    tri(topCenter,topRing+i,topRing+i+1)
                }
            }
        }
        val pa = p.toFloatArray()
        var minX = Float.POSITIVE_INFINITY; var minY = minX; var minZ = minX
        var maxX = Float.NEGATIVE_INFINITY; var maxY = maxX; var maxZ = maxX
        for (i in pa.indices step 3) {
            minX=min(minX,pa[i]); minY=min(minY,pa[i+1]); minZ=min(minZ,pa[i+2])
            maxX=max(maxX,pa[i]); maxY=max(maxY,pa[i+1]); maxZ=max(maxZ,pa[i+2])
        }
        return Geometry(pa,n.toFloatArray(),idx.toIntArray(),minX,minY,minZ,maxX,maxY,maxZ)
    }

    companion object {
        private var nextId = 1
        fun demo(): SceneDocument = SceneDocument().apply { add(PrimitiveKind.CUBE) }
    }
}
