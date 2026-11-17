package xyz.wallpanel.app.modules

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One owned frame and one worker. A camera callback buffer may be reused as
 * soon as the callback returns, so copy it before submitting asynchronous work.
 */
internal class JpegWorker(context: Context, private val publish: (ByteArray) -> Unit) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "WallPanel-JPEG") }
    private val active = AtomicBoolean(true)
    private val busy = AtomicBoolean(false)
    private var frameBuffer = ByteArray(0)
    private var encoder: JpegEncoder? = null

    fun submit(frame: ByteArray, width: Int, height: Int, rotation: Int): Boolean {
        if (!active.get() || !busy.compareAndSet(false, true)) return false
        if (frameBuffer.size != frame.size) frameBuffer = ByteArray(frame.size)
        System.arraycopy(frame, 0, frameBuffer, 0, frame.size)
        try {
            executor.execute {
                try {
                    if (active.get()) {
                        val converter = encoder ?: JpegEncoder(appContext).also { encoder = it }
                        val jpeg = converter.encode(frameBuffer, width, height, rotation)
                        if (active.get()) publish(jpeg)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "JPEG conversion failed")
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
            return false
        }
        return true
    }

    fun close() {
        if (!active.getAndSet(false)) return
        // Queue cleanup behind the current frame; never destroy an in-use RS allocation.
        executor.execute {
            try {
                encoder?.close()
            } finally {
                encoder = null
                frameBuffer = ByteArray(0)
            }
        }
        executor.shutdown()
    }

    private class JpegEncoder(context: Context) {
        private val rs = RenderScript.create(context)
        private val converter = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
        private var inputType: Type? = null
        private var outputType: Type? = null
        private var input: Allocation? = null
        private var output: Allocation? = null
        private var bitmap: Bitmap? = null
        private var width = 0
        private var height = 0
        private var byteCount = 0
        private val jpegOutput = ByteArrayOutputStream(64 * 1024)

        fun encode(frame: ByteArray, frameWidth: Int, frameHeight: Int, rotation: Int): ByteArray {
            if (width != frameWidth || height != frameHeight || byteCount != frame.size) {
                releaseBuffers()
                inputType = Type.Builder(rs, Element.U8(rs)).setX(frame.size).create()
                outputType = Type.Builder(rs, Element.RGBA_8888(rs))
                    .setX(frameWidth).setY(frameHeight).create()
                input = Allocation.createTyped(rs, inputType, Allocation.USAGE_SCRIPT)
                output = Allocation.createTyped(rs, outputType, Allocation.USAGE_SCRIPT)
                bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
                converter.setInput(input)
                width = frameWidth
                height = frameHeight
                byteCount = frame.size
            }
            input!!.copyFrom(frame)
            converter.forEach(output)
            val source = bitmap!!
            output!!.copyTo(source)
            val angle = ((rotation % 360) + 360) % 360
            val image = if (angle == 0) source else {
                val matrix = Matrix().apply { postRotate(angle.toFloat()) }
                Bitmap.createBitmap(source, 0, 0, width, height, matrix, true)
            }
            try {
                jpegOutput.reset()
                check(image.compress(Bitmap.CompressFormat.JPEG, 80, jpegOutput))
                return jpegOutput.toByteArray()
            } finally {
                if (image !== source) image.recycle()
            }
        }

        private fun releaseBuffers() {
            input?.destroy()
            output?.destroy()
            inputType?.destroy()
            outputType?.destroy()
            bitmap?.recycle()
            input = null
            output = null
            inputType = null
            outputType = null
            bitmap = null
            width = 0
            height = 0
            byteCount = 0
        }

        fun close() {
            releaseBuffers()
            converter.destroy()
            // On Android 7 create() uses a process-wide cached context; destroy()
            // deliberately leaves that shared context alive.
            rs.destroy()
            jpegOutput.close()
        }
    }
}
