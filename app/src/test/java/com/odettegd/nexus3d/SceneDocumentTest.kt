package com.odettegd.nexus3d

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SceneDocumentTest {
    @Test
    fun transformsAndNamesRoundTripThroughVersionedSceneJson() {
        val scene = SceneDocument()
        scene.add(PrimitiveKind.SPHERE)
        scene.renameSelected("Hero Sphere")
        scene.translateSelected(1.25f, -2f, 0.5f)
        scene.rotateSelected(10f, 30f, 0f)
        scene.scaleSelected(1.5f)

        val restored = SceneDocument()
        restored.loadJson(scene.toJson())
        val node = restored.selected()!!

        assertEquals("Hero Sphere", node.name)
        assertEquals(1.25f, node.x, 0.0001f)
        assertEquals(-2f, node.y, 0.0001f)
        assertEquals(30f, node.ry, 0.0001f)
        assertEquals(1.5f, node.sx, 0.0001f)
        assertEquals(PrimitiveKind.SPHERE, node.primitive)
    }

    @Test
    fun duplicateAndDeleteOperateOnSelectedObject() {
        val scene = SceneDocument.demo()
        val copy = scene.duplicateSelected()
        assertNotNull(copy)
        assertEquals(2, scene.objects.size)
        assertEquals(1, scene.selectedIndex)
        assertTrue(scene.deleteSelected())
        assertEquals(1, scene.objects.size)
        assertFalse(scene.deleteSelected().not())
    }

    @Test
    fun glbHasValidHeaderChunksAndRealMeshAccessors() {
        val scene = SceneDocument()
        scene.add(PrimitiveKind.CUBE)
        scene.add(PrimitiveKind.SPHERE)
        scene.add(PrimitiveKind.PLANE)
        scene.add(PrimitiveKind.CYLINDER)
        val bytes = scene.toGlb()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x46546C67, header.getInt(0))
        assertEquals(2, header.getInt(4))
        assertEquals(bytes.size, header.getInt(8))
        val jsonLength = header.getInt(12)
        assertEquals(0x4E4F534A, header.getInt(16))
        val json = String(bytes, 20, jsonLength, Charsets.UTF_8).trim()
        val root = JSONObject(json)
        assertEquals(4, root.getJSONArray("nodes").length())
        assertEquals(4, root.getJSONArray("meshes").length())
        assertTrue(root.getJSONArray("accessors").length() >= 12)
        val binHeader = 20 + jsonLength
        assertTrue(header.getInt(binHeader) > 0)
        assertEquals(0x004E4942, header.getInt(binHeader + 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSceneVersion() {
        SceneDocument().loadJson("""{"format":"nexus-scene","version":999,"nodes":[]}""")
    }

    @Test
    fun invalidScaleIsRejectedAndScaleStaysPositive() {
        val scene = SceneDocument.demo()
        assertFalse(scene.scaleSelected(Float.NaN))
        assertFalse(scene.scaleSelected(-1f))
        assertTrue(scene.scaleSelected(1.25f))
        assertTrue(scene.selected()!!.sx > 0f)
    }
}
