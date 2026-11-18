package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SensorMappingTest {
    /** 4000x3000 のセンサーと 1920x1440 のストリーム。base はセンサー全体。 */
    private fun spec(rotation: Int, mirror: Boolean, maxZoom: Float = 8f, freeform: Boolean = true, active: Pair<Int, Int> = 4000 to 3000) =
        SensorSpec(active.first, active.second, maxZoom, freeform, 1920, 1440, rotation, mirror)

    /** 正立させた像がちょうど収まる 0.4 倍のビュー。センサーの 2.5 画素がビューの 1 画素。 */
    private fun fittedView(rotation: Int) = if (rotation % 180 == 0) Size(1600f, 1200f) else Size(1200f, 1600f)

    private val cases = listOf(0, 90, 180, 270).flatMap { r -> listOf(r to false, r to true) }

    /**
     * 正立させると、センサーのどの角が画面の左上に来るか。時計回りに r 度回すと、左下(90)、右下(180)、右上(270)が左上へ来る。
     * ミラーは画面で左右を入れ替えるので、画面の左上には正立像の右上が来る。
     */
    private val sensorCornerAtScreenTopLeft = mapOf(
        (0 to false) to "TL", (0 to true) to "TR",
        (90 to false) to "BL", (90 to true) to "TL",
        (180 to false) to "BR", (180 to true) to "BL",
        (270 to false) to "TR", (270 to true) to "BR",
    )

    private fun corner(r: SensorRect, s: SensorSpec) =
        (if (r.top == 0) "T" else if (r.bottom == s.activeHeight) "B" else "?") +
            (if (r.left == 0) "L" else if (r.right == s.activeWidth) "R" else "?")

    @Test
    fun theTopLeftOfTheScreenComesFromTheSensorCornerTheRotationAndMirrorBringThere() {
        for ((rotation, mirror) in cases) {
            val s = spec(rotation, mirror)
            val view = fittedView(rotation)
            val topLeft = Crop(0f, 0f, 8f).clamp(view)
            val rect = SensorMapping(s, view).toSensor(topLeft)
            assertEquals("r=$rotation mirror=$mirror $rect", sensorCornerAtScreenTopLeft.getValue(rotation to mirror), corner(rect, s))
            assertEquals(500, rect.width)
            assertEquals(375, rect.height)
        }
    }

    @Test
    fun anOffCentreCropReadsTheMatchingPartOfTheSensor() {
        // ビューの左 1/4、上から 3/8 の点を中心に 4 倍。正立像では (1000, 1125) 画素(横長)か (750, 1500) 画素(縦長)で、切り出しは 1000x750。
        val expected = mapOf(
            (0 to false) to SensorRect(500, 750, 1500, 1500),
            (0 to true) to SensorRect(2500, 750, 3500, 1500),
            (90 to false) to SensorRect(1000, 1875, 2000, 2625),
            (90 to true) to SensorRect(1000, 375, 2000, 1125),
            (180 to false) to SensorRect(2500, 1500, 3500, 2250),
            (180 to true) to SensorRect(500, 1500, 1500, 2250),
            (270 to false) to SensorRect(2000, 375, 3000, 1125),
            (270 to true) to SensorRect(2000, 1875, 3000, 2625),
        )
        for ((rotation, mirror) in cases) {
            val view = fittedView(rotation)
            val shown = Crop(view.w / 4, view.h * 3 / 8, 4f)
            val mapping = SensorMapping(spec(rotation, mirror), view)
            val rect = mapping.toSensor(mapping.cameraPart(shown))
            assertEquals("r=$rotation mirror=$mirror", expected.getValue(rotation to mirror), rect)
            val back = mapping.toView(rect)
            assertPt(shown.center, back.center, eps = 0.01f)
            assertEquals(4f, back.zoom, 1e-4f)
        }
    }

    /** 前面カメラの縦持ち(270 度、ミラー)を 1080x1920 に FILL_CENTER。正立像 3000x4000 を 0.48 倍して幅 1440 にし、左右 180 ずつ切れる。 */
    private val phone = SensorMapping(spec(270, mirror = true), PHONE)

    @Test
    fun theWholeViewAsksForTheWholeSensor() {
        assertEquals(SensorRect(0, 0, 4000, 3000), phone.toSensor(phone.cameraPart(Crop.full(PHONE))))
        val whole = phone.toView(SensorRect(0, 0, 4000, 3000))
        assertPt(PHONE.center, whole.center, eps = 1e-3f)
        assertEquals(1f, whole.zoom, 1e-6f)
    }

    @Test
    fun theSidesFillCenterCutsOffAreAccountedFor() {
        // ビューの x=300 はストリームの 480/1440 = 正立像の 1000/3000。ミラーで 2000、270 度戻すとセンサーの y になる。
        val rect = phone.toSensor(phone.cameraPart(Crop(300f, 1200f, 2f)))
        assertEquals(SensorRect(500, 1250, 2500, 2750), rect)
    }

    @Test
    fun aWideSensorUsesTheCentredFourByThreePartAndZoomsLess() {
        val wide = spec(270, mirror = true, active = 4000 to 2250)
        assertEquals(3000f, wide.baseWidth)
        assertEquals(500f, wide.baseLeft)
        assertEquals(6f, wide.zoomLimit, 1e-5f)
        val mapping = SensorMapping(wide, PHONE)
        assertEquals(SensorRect(500, 0, 3500, 2250), mapping.toSensor(mapping.cameraPart(Crop.full(PHONE))))
        assertEquals(1f, mapping.toView(SensorRect(0, 0, 4000, 2250)).zoom, 1e-6f)
    }

    @Test
    fun cropsSurviveTheRoundTripToTheSensorAndBack() {
        val rnd = Random(9)
        for ((rotation, mirror) in cases) {
            val mapping = SensorMapping(spec(rotation, mirror, maxZoom = 10f), PHONE)
            repeat(300) {
                val shown = Crop(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h, 1f + rnd.nextFloat() * 9f).clamp(PHONE)
                val back = mapping.toView(mapping.toSensor(shown))
                assertPt(shown.center, back.center, eps = 1.5f)
                assertEquals(shown.zoom, back.zoom, shown.zoom * 0.01f)
            }
        }
    }

    @Test
    fun theCameraTakesTheWholeZoomUpToItsLimit() {
        val shown = Crop(300f, 700f, 4f)
        assertEquals(shown, phone.cameraPart(shown))
    }

    @Test
    fun pastItsLimitTheCameraStopsThereAndTheAppMagnifiesTheRest() {
        val shown = Crop(540f, 960f, 10f)
        val camera = phone.cameraPart(shown)
        assertEquals(8f, camera.zoom)
        assertEquals(1.25f, shown.within(camera, PHONE).zoom, 1e-5f)
    }

    /** カメラの切り出しがビューの中に収まり、shown の表示領域を含んでいれば、残りの拡大で画像の外は見えない。 */
    private fun assertCameraCovers(shown: Crop, camera: Crop) {
        assertShowsOnlyImage(camera, PHONE)
        assertShowsOnlyImage(shown.within(camera, PHONE), PHONE)
    }

    @Test
    fun theCameraCropAlwaysCoversWhatIsShownAndStaysInsideTheImage() {
        val rnd = Random(4)
        for (freeform in listOf(true, false)) {
            for (limit in listOf(1.5f, 3f, 8f)) {
                val mapping = SensorMapping(spec(270, mirror = true, maxZoom = limit, freeform = freeform), PHONE)
                repeat(500) {
                    val shown = Crop(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h, 1f + rnd.nextFloat() * 9f).clamp(PHONE)
                    assertCameraCovers(shown, mapping.cameraPart(shown))
                }
            }
        }
    }

    @Test
    fun aCameraThatOnlyCropsTheCentreZoomsAsFarAsTheShownAreaAllows() {
        val centred = SensorMapping(spec(270, mirror = true, freeform = false), PHONE)
        assertEquals(Crop(540f, 960f, 5f), centred.cameraPart(Crop(540f, 960f, 5f)))
        // 中心から右へ 270 ずれた 4 倍の表示領域は x が 675..945。それを含む中央の範囲は半幅 405 までで、倍率 540/405。
        val camera = centred.cameraPart(Crop(810f, 960f, 4f))
        assertPt(PHONE.center, camera.center, eps = 1e-3f)
        assertEquals(540f / 405f, camera.zoom, 1e-4f)
    }

    @Test
    fun withoutCropSupportNothingIsAskedOfTheCamera() {
        assertNull(SensorSpec.of(null, 3000, 8f, true, 1920, 1440, 270, true))
        assertNull(SensorSpec.of(4000, 3000, null, true, 1920, 1440, 270, true))
        assertNull(SensorSpec.of(4000, 3000, 1f, true, 1920, 1440, 270, true))
        assertNull(SensorSpec.of(4000, 3000, 8f, true, null, 1440, 270, true))
        assertNull(SensorSpec.of(4000, 3000, 8f, true, 1920, 1440, 45, true))
        assertEquals(spec(270, mirror = true), SensorSpec.of(4000, 3000, 8f, true, 1920, 1440, -90, true))
    }

    @Test
    fun theCameraLimitComesFromTheDevice() {
        assertEquals(4f, spec(270, true, maxZoom = 4f).zoomLimit)
        assertEquals(4f, SensorMapping(spec(270, true, maxZoom = 4f), PHONE).cameraPart(Crop(540f, 960f, 10f)).zoom)
    }

    @Test
    fun aFrameCountsAsTheWholeViewOnlyWhenTheCameraIsNotZoomedIn() {
        val s = spec(270, mirror = true)
        assertTrue(s.showsAll(SensorRect(0, 0, 4000, 3000)))
        assertTrue(s.showsAll(SensorRect(20, 15, 3980, 2985)))
        assertTrue(!s.showsAll(SensorRect(200, 150, 3800, 2850)))
        assertEquals(2f, s.zoomOf(SensorRect(1000, 750, 3000, 2250)), 1e-6f)
    }
}
