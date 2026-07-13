package com.bestphotoselect.lite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.FaceDetector
import com.bestphotoselect.data.model.FaceMetrics
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Cihaz içi yüz analizi (lite sürüm):
 *  1. Android'in yerleşik [FaceDetector]'ü yüzleri bulur (göz arası mesafe + merkez verir).
 *  2. Her yüz kırpılıp MediaPipe Face Mesh TFLite modeliyle (468 nokta) işlenir.
 *  3. Noktalardan türetilir: EAR (göz açıklık oranı) -> gözler açık mı,
 *     burun-yanak simetrisi + göz hattı eğimi -> yüz kameraya dönük mü,
 *     ağız köşesi yüksekliği -> gülümseme.
 *
 * Model APK assets içinde paketlidir; internet gerektirmez.
 */
class FaceAnalyzerLite(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var interpreter: Interpreter? = null

    @Synchronized
    private fun tflite(): Interpreter? {
        interpreter?.let { return it }
        return try {
            Interpreter(loadModel(), Interpreter.Options().apply { setNumThreads(2) })
                .also { interpreter = it }
        } catch (t: Throwable) {
            null
        }
    }

    private fun loadModel(): MappedByteBuffer {
        appContext.assets.openFd(MODEL_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { ch ->
                return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    /**
     * Yalnızca yüz sayısını döndüren ucuz kontrol: TFLite yüz-noktası analizini
     * ÇALIŞTIRMAZ, yalnızca yerleşik [FaceDetector] ile kaba kutu tespiti yapar.
     * Gruplama aşamasında "aynı arka plan, farklı kişi sayısı" gibi yanlış
     * eşleşmeleri elemek için tüm fotoğraflarda ucuza çalıştırılabilir.
     */
    fun countFaces(src: Bitmap): Int = try {
        detectFaces(src).size
    } catch (t: Throwable) {
        -1
    }

    fun analyze(src: Bitmap): FaceMetrics? {
        val boxes = detectFaces(src)
        if (boxes.isEmpty()) return null

        var totalWeight = 0f
        var eyesOpen = 0f
        var frontal = 0f
        var smile = 0f
        var mouthClosed = 0f
        var areaSum = 0f
        var scoredFaces = 0

        for (box in boxes) {
            val area = box.width() * box.height()
            areaSum += area
            val scores = landmarkScores(src, box)
            if (scores != null) {
                val w = max(1f, area)
                eyesOpen += scores.eyesOpen * w
                frontal += scores.frontal * w
                smile += scores.smile * w
                mouthClosed += scores.mouthClosed * w
                totalWeight += w
                scoredFaces++
            }
        }

        if (scoredFaces == 0) {
            // Landmark modeli çalışmadıysa yüz varlığı bilgisiyle nötr metrikler döndür.
            return FaceMetrics(
                faceCount = boxes.size,
                eyesOpen = 0.7f,
                frontal = 0.7f,
                smile = 0.5f,
                mouthClosed = 0.7f,
                faceAreaRatio = min(1f, areaSum / (src.width * src.height))
            )
        }

        return FaceMetrics(
            faceCount = boxes.size,
            eyesOpen = (eyesOpen / totalWeight).coerceIn(0f, 1f),
            frontal = (frontal / totalWeight).coerceIn(0f, 1f),
            smile = (smile / totalWeight).coerceIn(0f, 1f),
            mouthClosed = (mouthClosed / totalWeight).coerceIn(0f, 1f),
            faceAreaRatio = min(1f, areaSum / (src.width * src.height))
        )
    }

    /** Yerleşik FaceDetector ile yüz kutuları (kaynak bitmap koordinatlarında). */
    private fun detectFaces(src: Bitmap): List<RectF> {
        val scale = min(1f, DETECT_SIZE.toFloat() / max(src.width, src.height))
        var w = (src.width * scale).toInt()
        val h = (src.height * scale).toInt()
        if (w % 2 == 1) w -= 1 // FaceDetector çift genişlik ister
        if (w < 32 || h < 32) return emptyList()

        val bmp565 = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        Canvas(bmp565).drawBitmap(src, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

        val detector = FaceDetector(w, h, MAX_FACES)
        val found = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val n = try {
            detector.findFaces(bmp565, found)
        } catch (t: Throwable) {
            0
        } finally {
            bmp565.recycle()
        }

        val sx = src.width.toFloat() / w
        val boxes = ArrayList<RectF>(n)
        val mid = android.graphics.PointF()
        for (i in 0 until n) {
            val face = found[i] ?: continue
            if (face.confidence() < 0.3f) continue
            face.getMidPoint(mid)
            val eyeDist = face.eyesDistance()
            // Göz orta noktası + göz mesafesinden kare yüz kutusu tahmini.
            val cx = mid.x * sx
            val cy = (mid.y + eyeDist * 0.55f) * sx
            val half = eyeDist * 2.1f * sx
            boxes += RectF(cx - half, cy - half, cx + half, cy + half)
        }
        return boxes
    }

    private class Scores(val eyesOpen: Float, val frontal: Float, val smile: Float, val mouthClosed: Float)

    private fun landmarkScores(src: Bitmap, box: RectF): Scores? {
        val itp = tflite() ?: return null

        // Kutunun görüntü içinde kalan kare kırpımı
        val left = box.left.coerceIn(0f, (src.width - 2).toFloat()).toInt()
        val top = box.top.coerceIn(0f, (src.height - 2).toFloat()).toInt()
        val right = box.right.coerceIn((left + 2).toFloat(), src.width.toFloat()).toInt()
        val bottom = box.bottom.coerceIn((top + 2).toFloat(), src.height.toFloat()).toInt()

        val crop = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(crop).drawBitmap(
            src, Rect(left, top, right, bottom),
            Rect(0, 0, INPUT_SIZE, INPUT_SIZE), Paint(Paint.FILTER_BITMAP_FLAG)
        )

        val input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        crop.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        crop.recycle()
        for (p in pixels) {
            input.putFloat(((p shr 16) and 0xFF) / 255f)
            input.putFloat(((p shr 8) and 0xFF) / 255f)
            input.putFloat((p and 0xFF) / 255f)
        }
        input.rewind()

        // Çıktı tensörleri: 1404 float (468x3 nokta) + 1 float (yüz skoru)
        val outputs = HashMap<Int, Any>()
        var landmarkIdx = -1
        var scoreIdx = -1
        val buffers = arrayOfNulls<ByteBuffer>(itp.outputTensorCount)
        for (i in 0 until itp.outputTensorCount) {
            val shape = itp.getOutputTensor(i).shape()
            var elems = 1
            for (d in shape) elems *= d
            val buf = ByteBuffer.allocateDirect(elems * 4).order(ByteOrder.nativeOrder())
            buffers[i] = buf
            outputs[i] = buf
            if (elems == LANDMARK_FLOATS) landmarkIdx = i
            if (elems == 1) scoreIdx = i
        }
        if (landmarkIdx < 0) return null

        try {
            itp.runForMultipleInputsOutputs(arrayOf<Any>(input), outputs)
        } catch (t: Throwable) {
            return null
        }

        if (scoreIdx >= 0) {
            val raw = buffers[scoreIdx]!!.apply { rewind() }.float
            val score = if (raw < -20f || raw > 20f) 1f else 1f / (1f + exp(-raw))
            if (score < 0.15f) return null
        }

        val lmBuf = buffers[landmarkIdx]!!.apply { rewind() }
        val lm = FloatArray(LANDMARK_FLOATS)
        lmBuf.asFloatBuffer().get(lm)
        fun x(i: Int) = lm[i * 3]
        fun y(i: Int) = lm[i * 3 + 1]
        fun dist(a: Int, b: Int) = hypot((x(a) - x(b)).toDouble(), (y(a) - y(b)).toDouble()).toFloat()

        // Göz açıklık oranı (EAR) — her iki göz
        fun ear(h1: Int, h2: Int, v1a: Int, v1b: Int, v2a: Int, v2b: Int): Float {
            val hd = dist(h1, h2)
            if (hd < 1e-3f) return 0f
            return (dist(v1a, v1b) + dist(v2a, v2b)) / (2f * hd)
        }
        val earRight = ear(33, 133, 160, 144, 158, 153)
        val earLeft = ear(362, 263, 385, 380, 387, 373)
        fun earToOpen(e: Float) = ((e - EAR_CLOSED) / (EAR_OPEN - EAR_CLOSED)).coerceIn(0f, 1f)
        val eyesOpen = (earToOpen(earLeft) + earToOpen(earRight)) / 2f

        // Yaw: burun ucunun (1) sol (234) / sağ (454) yanak kenarlarına uzaklık simetrisi
        val dl = abs(x(1) - x(234))
        val dr = abs(x(454) - x(1))
        val yawScore = if (max(dl, dr) < 1e-3f) 0f else min(dl, dr) / max(dl, dr)

        // Roll: göz merkezleri arasındaki eğim
        val rx = (x(33) + x(133)) / 2f
        val ry = (y(33) + y(133)) / 2f
        val lx = (x(362) + x(263)) / 2f
        val ly = (y(362) + y(263)) / 2f
        val rollDeg = Math.toDegrees(atan2((ly - ry).toDouble(), (lx - rx).toDouble())).toFloat()
        val rollScore = 1f - min(1f, abs(rollDeg) / 45f)

        val frontal = (yawScore * 0.75f + rollScore * 0.25f).coerceIn(0f, 1f)

        // Gülümseme: ağız köşeleri (61, 291) ağız merkezinden ne kadar yukarıda?
        val mouthWidth = dist(61, 291)
        val smile = if (mouthWidth < 1e-3f) 0.5f else {
            val cornersY = (y(61) + y(291)) / 2f
            val centerY = (y(13) + y(14)) / 2f
            (0.5f + (centerY - cornersY) / mouthWidth * 3f).coerceIn(0f, 1f)
        }

        // Ağız doğallığı: üst/alt dudak iç kenarları (13, 14) arasındaki açıklık,
        // ağız genişliğine oranlanır (MAR benzeri). Konuşma anı/esneme gibi geniş
        // açık ağızlarda oran yükselir; doğal kapalı/hafif gülümseyen ağızda düşük
        // kalır. 61/291 zaten mouthWidth için hesaplandı, tekrar kullanılır.
        val mouthGap = dist(13, 14)
        val mouthOpenRatio = if (mouthWidth < 1e-3f) 0f else mouthGap / mouthWidth
        val mouthClosed = (1f - (mouthOpenRatio - MOUTH_RATIO_CLOSED) /
            (MOUTH_RATIO_OPEN - MOUTH_RATIO_CLOSED)).coerceIn(0f, 1f)

        return Scores(eyesOpen, frontal, smile, mouthClosed)
    }

    companion object {
        private const val MODEL_ASSET = "face_landmark.tflite"
        private const val DETECT_SIZE = 512
        private const val MAX_FACES = 4
        private const val INPUT_SIZE = 192
        private const val LANDMARK_FLOATS = 468 * 3
        private const val EAR_CLOSED = 0.10f
        private const val EAR_OPEN = 0.26f
        private const val MOUTH_RATIO_CLOSED = 0.03f
        private const val MOUTH_RATIO_OPEN = 0.35f
    }
}
