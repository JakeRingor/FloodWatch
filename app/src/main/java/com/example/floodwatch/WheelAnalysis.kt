package com.example.floodwatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.FloatBuffer

object WheelAnalysis {
    data class Result(
        val preview: Bitmap,
        val description: String,
        val estimatedDepthCm: Float?,
        val wheelCount: Int,
        val highestConfidence: Float?,
        val submergedFraction: Float?
    )
    private val lock = Mutex()
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    private fun session(context: Context): OrtSession {
        session?.let { return it }
        val model = File(context.filesDir, "wheel_detector_v1.onnx")
        context.assets.open("wheel_detector.onnx").use { source ->
            if (!model.exists() || model.length() != 122292660L) {
                val temporary = File(context.filesDir, "wheel_detector.tmp")
                temporary.outputStream().use { source.copyTo(it) }
                check(temporary.renameTo(model)) { "Unable to prepare wheel model" }
            }
        }
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            environment.createSession(model.absolutePath, options).also { session = it }
        }
    }

    suspend fun analyze(context: Context, original: Bitmap): Result = withContext(Dispatchers.Default) {
        lock.withLock {
            val inputBitmap = Bitmap.createScaledBitmap(original, 512, 512, true)
            val pixels = IntArray(512 * 512)
            inputBitmap.getPixels(pixels, 0, 512, 0, 0, 512, 512)
            if (inputBitmap !== original) inputBitmap.recycle()
            val values = FloatArray(pixels.size * 3)
            val means = floatArrayOf(.485f, .456f, .406f)
            val stds = floatArrayOf(.229f, .224f, .225f)
            for (i in pixels.indices) {
                val channels = intArrayOf(Color.red(pixels[i]), Color.green(pixels[i]), Color.blue(pixels[i]))
                for (c in 0..2) values[c * pixels.size + i] = (channels[c] / 255f - means[c]) / stds[c]
            }
            val detector = session(context.applicationContext)
            val boxes = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), longArrayOf(1, 3, 512, 512)).use { tensor ->
                detector.run(mapOf("input" to tensor)).use { output ->
                    @Suppress("UNCHECKED_CAST")
                    val coordinates = (output.get("dets").get().value as Array<Array<FloatArray>>)[0]
                    @Suppress("UNCHECKED_CAST")
                    val logits = (output.get("labels").get().value as Array<Array<FloatArray>>)[0]
                    WheelDetections.decode(coordinates, logits)
                }
            }
            val preview = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(preview)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = maxOf(3f, original.width / 250f)
            }
            for (box in boxes) {
                canvas.drawRect(box.left * original.width, box.top * original.height,
                    box.right * original.width, box.bottom * original.height, paint)
            }
            var estimatedDepthCm: Float? = null
            var submergedFraction: Float? = null
            val description = if (boxes.isEmpty()) {
                "No wheel detected.\nSubmerged: unavailable\nDepth: unavailable"
            } else {
                val heading = "${boxes.size} wheel(s) found. Highest confidence: %.1f%%".format(boxes.first().score * 100)
                val best = boxes.first()
                val clipped = best.left <= 0f || best.top <= 0f || best.right >= 1f || best.bottom >= 1f
                val left = (best.left * original.width).toInt().coerceIn(0, original.width - 1)
                val top = (best.top * original.height).toInt().coerceIn(0, original.height - 1)
                val right = (best.right * original.width).toInt().coerceIn(left + 1, original.width)
                val bottom = (best.bottom * original.height).toInt().coerceIn(top + 1, original.height)
                val width = right - left
                val height = bottom - top
                val row = if (clipped) null else {
                    // Read the original photo, before drawing boxes or the candidate waterline.
                    val cropPixels = IntArray(width * height)
                    original.getPixels(cropPixels, 0, width, left, top, width, height)
                    val gray = FloatArray(cropPixels.size)
                    val saturation = FloatArray(cropPixels.size)
                    val hsv = FloatArray(3)
                    for (i in cropPixels.indices) {
                        val pixel = cropPixels[i]
                        gray[i] = .299f * Color.red(pixel) + .587f * Color.green(pixel) + .114f * Color.blue(pixel)
                        Color.colorToHSV(pixel, hsv)
                        saturation[i] = hsv[1] * 255f
                    }
                    WheelWaterline.candidate(width, height, gray, saturation)
                }
                if (row == null) {
                    val reason = when {
                        clipped -> "Selected wheel is cut off by the image edge."
                        else -> "No clear waterline candidate on the selected wheel."
                    }
                    "$heading\nSubmerged: unavailable\nDepth: unavailable\n$reason"
                } else {
                    val waterY = top + row
                    val fraction = WheelWaterline.fraction(top.toFloat(), bottom.toFloat(), waterY.toFloat())!!
                    submergedFraction = fraction
                    estimatedDepthCm = fraction * 60f
                    paint.color = Color.YELLOW
                    canvas.drawLine(left.toFloat(), waterY.toFloat(), right.toFloat(), waterY.toFloat(), paint)
                    "%s\nEstimated submerged height: %.1f%%\nEstimated depth: %.1f cm\nExperimental: assumes a full wheel and a 60 cm tire diameter. Yellow line marks an unverified waterline candidate on the highest-confidence wheel. Confirm manually."
                        .format(heading, fraction * 100, estimatedDepthCm)
                }
            }
            Result(
                preview = preview,
                description = description,
                estimatedDepthCm = estimatedDepthCm,
                wheelCount = boxes.size,
                highestConfidence = boxes.firstOrNull()?.score,
                submergedFraction = submergedFraction
            )
        }
    }
}
