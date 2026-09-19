package com.example.floodwatch

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

object FloodClassifier {
    const val THRESHOLD = 0.4f

    fun predict(context: Context, bitmap: Bitmap): Float {
        val bytes = context.assets.open("floodwatch_model.tflite").use { it.readBytes() }
        val modelBuffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        modelBuffer.put(bytes).rewind()
        Interpreter(modelBuffer, Interpreter.Options().setNumThreads(2)).use { interpreter ->
            require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3)))
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 1)))
            require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
            val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val pixels = IntArray(224 * 224)
            resized.getPixels(pixels, 0, 224, 0, 0, 224, 224)
            if (resized !== bitmap) resized.recycle()
            val input = ByteBuffer.allocateDirect(pixels.size * 3 * 4).order(ByteOrder.nativeOrder())
            for (pixel in pixels) {
                input.putFloat(((pixel shr 16) and 255).toFloat())
                input.putFloat(((pixel shr 8) and 255).toFloat())
                input.putFloat((pixel and 255).toFloat())
            }
            input.rewind()
            val output = arrayOf(FloatArray(1))
            interpreter.run(input, output)
            return output[0][0].also { require(it.isFinite() && it in 0f..1f) }
        }
    }
}
