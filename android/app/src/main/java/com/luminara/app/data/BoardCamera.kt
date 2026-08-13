package com.luminara.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * The camera for Live Class, kept deliberately small.
 *
 * It shows a preview and hands back a single JPEG when asked. It never records
 * video and never streams frames to the backend: the class is the audio, and a
 * board capture is one photograph taken at one moment. A camera that fails to
 * start is reported and ignored — the lecture carries on without it.
 */
class BoardCamera(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null

    var lastError: String = ""
        private set

    val isReady: Boolean get() = capture != null

    /** Binds preview + capture. Returns null on success, or a readable reason. */
    suspend fun start(owner: LifecycleOwner, view: PreviewView): String? =
        suspendCoroutine { cont ->
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        val cameraProvider = future.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(view.surfaceProvider)
                        }
                        // MIN_LATENCY: a board photo needs to be legible, not
                        // beautiful, and the student is mid-class.
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            owner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                        provider = cameraProvider
                        capture = imageCapture
                        lastError = ""
                        cont.resume(null)
                    } catch (e: Exception) {
                        lastError = e.message ?: "the camera could not be started"
                        cont.resume(lastError)
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                lastError = e.message ?: "no camera is available on this device"
                cont.resume(lastError)
            }
        }

    /** One frame as JPEG bytes, already rotated upright. Null if unavailable. */
    suspend fun captureJpeg(): ByteArray? = suspendCoroutine { cont ->
        val imageCapture = capture
        if (imageCapture == null) {
            cont.resume(null)
            return@suspendCoroutine
        }
        try {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = try {
                            image.toUprightJpeg()
                        } catch (e: Exception) {
                            lastError = e.message ?: "could not read the captured frame"
                            null
                        } finally {
                            image.close()
                        }
                        cont.resume(bytes)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        lastError = exception.message ?: "the camera could not take the photo"
                        cont.resume(null)
                    }
                },
            )
        } catch (e: Exception) {
            lastError = e.message ?: "the camera is not ready"
            cont.resume(null)
        }
    }

    fun stop() {
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
            // Releasing a camera that is already gone is not an error worth showing.
        }
        provider = null
        capture = null
    }
}

/**
 * JPEG bytes the right way up.
 *
 * CameraX reports rotation separately from the pixels; sending the raw buffer
 * gives the vision pass a sideways board, and text read sideways is text read
 * wrongly. Rotating here costs a few milliseconds and removes that whole class
 * of failure.
 */
private fun ImageProxy.toUprightJpeg(quality: Int = 88): ByteArray {
    val buffer = planes[0].buffer
    val raw = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return raw

    val bitmap: Bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val rotated = Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        Matrix().apply { postRotate(degrees.toFloat()) },
        true,
    )
    return ByteArrayOutputStream().use { out ->
        rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }
}
