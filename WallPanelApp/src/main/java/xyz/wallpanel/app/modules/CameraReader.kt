/*
 * Copyright (c) 2022 WallPanel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.wallpanel.app.modules

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Camera
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.vision.*
import com.google.android.gms.vision.CameraSource.CAMERA_FACING_BACK
import com.google.android.gms.vision.CameraSource.CAMERA_FACING_FRONT
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.android.gms.vision.face.Face
import com.google.android.gms.vision.face.FaceDetector
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor
import xyz.wallpanel.app.persistence.Configuration
import xyz.wallpanel.app.ui.views.CameraSourcePreview
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject


class CameraReader @Inject
constructor(private val context: Context) {

    private var cameraCallback: CameraCallback? = null
    private var faceDetector: FaceDetector? = null
    private var barcodeDetector: BarcodeDetector? = null
    private var motionDetector: MotionDetector? = null
    private var multiDetector: MultiDetector? = null
    private var streamDetector: StreamingDetector? = null
    private var cameraSource: CameraSource? = null
    private var faceDetectorProcessor: LargestFaceFocusingProcessor? = null
    private var barCodeDetectorProcessor: MultiProcessor<Barcode>? = null
    private var motionDetectorProcessor: MultiProcessor<Motion>? = null
    private var streamDetectorProcessor: MultiProcessor<Stream>? = null
    private val byteArray = MutableLiveData<ByteArray>()
    private var cameraOrientation: Int = 0
    private var cameraPreview: CameraSourcePreview? = null
    @Volatile private var streamingEnabled = false
    @Volatile private var jpegWorker: JpegWorker? = null
    private var nextJpegFrameAt = 0L

    fun setStreamingEnabled(enabled: Boolean) {
        streamingEnabled = enabled
    }

    fun getJpeg(): LiveData<ByteArray> {
        return byteArray
    }

    fun stopCamera() {
        streamingEnabled = false
        cameraCallback = null
        cameraPreview?.stop()
        cameraPreview = null
        val source = cameraSource
        cameraSource = null
        // CameraSource owns MultiDetector, which owns the child detectors and processors.
        // Release that ownership tree once instead of releasing each child again.
        if (source != null) source.release() else multiDetector?.release()
        multiDetector = null
        faceDetector = null
        barcodeDetector = null
        motionDetector = null
        streamDetector = null
        faceDetectorProcessor = null
        barCodeDetectorProcessor = null
        motionDetectorProcessor = null
        streamDetectorProcessor = null
        jpegWorker?.close()
        jpegWorker = null
        nextJpegFrameAt = 0L
    }

    @SuppressLint("MissingPermission")
    fun startCamera(callback: CameraCallback, configuration: Configuration) {
        Timber.d("startCamera")
        if (cameraSource != null) {
            cameraCallback = callback
            return
        }
        stopCamera()
        this.cameraCallback = callback
        if (configuration.cameraEnabled) {
            buildDetectors(configuration)
            multiDetector?.let {
                try {
                    cameraSource = initCamera(configuration.cameraId, configuration.cameraFPS)
                    cameraSource?.start()
                } catch (e: Exception) {
                    Timber.e(e.message)
                    try {
                        if (configuration.cameraId == CAMERA_FACING_FRONT) {
                            cameraSource = initCamera(CAMERA_FACING_BACK, configuration.cameraFPS)
                            cameraSource?.start()
                        } else {
                            cameraSource = initCamera(CAMERA_FACING_FRONT, configuration.cameraFPS)
                            cameraSource?.start()
                        }
                    } catch (e: Exception) {
                        Timber.e(e.message)
                        cameraSource?.stop()
                        cameraCallback?.onCameraError()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun startCameraPreview(callback: CameraCallback, configuration: Configuration, preview: CameraSourcePreview?) {
        Timber.d("startCameraPreview")
        if (configuration.cameraEnabled && preview != null) {
            this.cameraCallback = callback
            this.cameraPreview = preview
            buildDetectors(configuration)
            if (multiDetector != null) {
                cameraSource = initCamera(configuration.cameraId, configuration.cameraFPS)
                cameraPreview?.start(cameraSource, object : CameraSourcePreview.OnCameraPreviewListener {
                    override fun onCameraError() {
                        Timber.e("Camera Preview Error")
                        cameraSource = if (configuration.cameraId == CAMERA_FACING_FRONT) {
                            initCamera(CAMERA_FACING_BACK, configuration.cameraFPS)
                        } else {
                            initCamera(CAMERA_FACING_FRONT, configuration.cameraFPS)
                        }
                        if (cameraPreview != null) {
                            try {
                                cameraPreview?.start(cameraSource, object : CameraSourcePreview.OnCameraPreviewListener {
                                    override fun onCameraError() {
                                        Timber.e("Camera Preview Error")
                                        cameraCallback?.onCameraError()
                                    }
                                })
                            } catch (e: Exception) {
                                Timber.e(e.message)
                                cameraPreview?.stop()
                                cameraSource?.stop()
                                cameraCallback?.onCameraError()
                            }
                        }
                    }
                })
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun startCameraPreviewSolo(callback: CameraCallback, configuration: Configuration, preview: CameraSourcePreview?) {
        Timber.d("startCameraPreviewSolo")
        if (configuration.cameraEnabled && preview != null) {
            this.cameraCallback = callback
            this.cameraPreview = preview
            buildCameraDetector(configuration)
            if (multiDetector != null) {
                cameraSource = initCamera(configuration.cameraId, configuration.cameraFPS)
                cameraPreview?.start(cameraSource, object : CameraSourcePreview.OnCameraPreviewListener {
                    override fun onCameraError() {
                        Timber.e("Camera Preview Error")
                        cameraSource = if (configuration.cameraId == CAMERA_FACING_FRONT) {
                            initCamera(CAMERA_FACING_BACK, configuration.cameraFPS)
                        } else {
                            initCamera(CAMERA_FACING_FRONT, configuration.cameraFPS)
                        }
                        if (cameraPreview != null) {
                            try {
                                cameraPreview?.start(cameraSource, object : CameraSourcePreview.OnCameraPreviewListener {
                                    override fun onCameraError() {
                                        Timber.e("Camera Preview Error")
                                        cameraCallback?.onCameraError()
                                    }
                                })
                            } catch (e: Exception) {
                                Timber.e(e.message)
                                cameraPreview?.stop()
                                cameraSource?.stop()
                                cameraCallback?.onCameraError()
                            }
                        }
                    }
                })
            }
        }
    }

    private fun buildCameraDetector(configuration: Configuration) {
        val info = Camera.CameraInfo()
        try {
            Camera.getCameraInfo(configuration.cameraId, info)
        } catch (e: RuntimeException) {
            Timber.e(e.message)
            cameraCallback?.onCameraError()
            return
        }
        cameraOrientation = info.orientation
        val multiDetectorBuilder = MultiDetector.Builder()
        var detectorAdded = false
        if (configuration.cameraEnabled) {
            streamDetector = StreamingDetector.Builder().build()
            streamDetectorProcessor = MultiProcessor.Builder<Stream> {
                object : Tracker<Stream>() {
                    // na-da
                }
            }.build()
            streamDetector?.setProcessor(streamDetectorProcessor!!)
            multiDetectorBuilder.add(streamDetector!!)
            detectorAdded = true
        }

        if (detectorAdded) {
            multiDetector = multiDetectorBuilder.build()
        }
    }

    private fun buildDetectors(configuration: Configuration) {
        val info = Camera.CameraInfo()
        try {
            Camera.getCameraInfo(configuration.cameraId, info)
        } catch (e: RuntimeException) {
            Timber.e(e.message)
            cameraCallback?.onCameraError()
            return
        }
        cameraOrientation = info.orientation
        val multiDetectorBuilder = MultiDetector.Builder()
        var detectorAdded = false
        if (configuration.cameraEnabled && configuration.httpMJPEGEnabled) {
            val worker = JpegWorker(context) { jpeg -> byteArray.postValue(jpeg) }
            jpegWorker = worker
            val frameDelay = when {
                configuration.cameraFPS <= 5 -> DELAY_5_FPS
                configuration.cameraFPS <= 10 -> DELAY_10_FPS
                configuration.cameraFPS <= 15 -> DELAY_15_FPS
                configuration.cameraFPS <= 20 -> DELAY_20_FPS
                else -> 0L
            }
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            streamDetector = StreamingDetector.Builder().build()
            streamDetectorProcessor = MultiProcessor.Builder<Stream> {
                object : Tracker<Stream>() {
                    override fun onUpdate(p0: Detector.Detections<Stream>, stream: Stream) {
                        if (!streamingEnabled) return
                        val frame = stream.byteArray ?: return
                        val width = stream.width ?: return
                        val height = stream.height ?: return
                        if (width <= 0 || height <= 0) return
                        val now = SystemClock.elapsedRealtime()
                        if (now < nextJpegFrameAt) return
                        val displayRotation = when (windowManager.defaultDisplay.rotation) {
                            Surface.ROTATION_90 -> 90
                            Surface.ROTATION_180 -> 180
                            Surface.ROTATION_270 -> 270
                            else -> 0
                        }
                        val rotation = cameraOrientation - displayRotation + configuration.cameraRotate.toInt()
                        if (worker.submit(frame, width, height, rotation)) {
                            nextJpegFrameAt = now + frameDelay
                        }
                    }
                }
            }.build()
            streamDetector?.setProcessor(streamDetectorProcessor!!)
            multiDetectorBuilder.add(streamDetector!!)
            detectorAdded = true
        }

        if (configuration.cameraEnabled && configuration.cameraMotionEnabled) {
            motionDetector = MotionDetector.Builder(configuration.cameraMotionMinLuma, configuration.cameraMotionLeniency).build()
            motionDetectorProcessor = MultiProcessor.Builder<Motion> {
                object : Tracker<Motion>() {
                    override fun onUpdate(p0: Detector.Detections<Motion>, motion: Motion) {
                        super.onUpdate(p0, motion)
                        if (cameraCallback != null && configuration.cameraMotionEnabled) {
                            if (Motion.MOTION_TOO_DARK == motion.type) {
                                 cameraCallback?.onTooDark()
                            } else if (Motion.MOTION_DETECTED == motion.type) {
                                cameraCallback?.onMotionDetected()
                            }
                        }
                    }
                }
            }.build()
            motionDetector?.setProcessor(motionDetectorProcessor!!)
            multiDetectorBuilder.add(motionDetector!!)
            detectorAdded = true
        }

        if (configuration.cameraEnabled && configuration.cameraFaceEnabled) {
            try {
                faceDetector = FaceDetector.Builder(context)
                        .setProminentFaceOnly(true)
                        .setTrackingEnabled(false)
                        .setMode(FaceDetector.FAST_MODE)
                        .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                        .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                        .build()

                faceDetectorProcessor = LargestFaceFocusingProcessor(faceDetector!!, object : Tracker<Face>() {
                    override fun onUpdate(detections: Detector.Detections<Face>, face: Face) {
                        super.onUpdate(detections, face)
                        val faceSize = face.width / detections.frameMetadata.width * 100 > configuration.cameraFaceSize;
                        val faceRotation = if (configuration.cameraFaceRotation) face.eulerY > -12 && face.eulerY < 12 else true;
                        if (detections.detectedItems.size() > 0 && faceSize && faceRotation) {
                            if (cameraCallback != null && configuration.cameraFaceEnabled) {
                                Timber.d("faceDetected")
                                cameraCallback?.onFaceDetected()
                            }
                        }
                    }
                })
                faceDetector?.setProcessor(faceDetectorProcessor!!)
                multiDetectorBuilder.add(faceDetector!!)
                detectorAdded = true
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        if (configuration.cameraEnabled && configuration.cameraQRCodeEnabled) {
            barcodeDetector = BarcodeDetector.Builder(context)
                    .setBarcodeFormats(Barcode.QR_CODE)
                    .build()
            barCodeDetectorProcessor = MultiProcessor.Builder<Barcode>(MultiProcessor.Factory<Barcode> {
                object : Tracker<Barcode>() {
                    override fun onUpdate(p0: Detector.Detections<Barcode>, p1: Barcode) {
                        super.onUpdate(p0, p1)
                        if (cameraCallback != null && configuration.cameraQRCodeEnabled) {
                            Timber.d("Barcode: " + p1.displayValue)
                            cameraCallback?.onQRCode(p1.displayValue)
                        }
                    }
                }
            }).build()
            barcodeDetector?.setProcessor(barCodeDetectorProcessor!!)
            multiDetectorBuilder.add(barcodeDetector!!)
            detectorAdded = true
        }

        if (detectorAdded) {
            multiDetector = multiDetectorBuilder.build()
            multiDetector?.let {
                if (it.isOperational.not()) {
                    cameraCallback?.onDetectorError()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initCamera(camerId: Int, fsp: Float): CameraSource {
        return CameraSource.Builder(context, multiDetector!!)
                .setRequestedFps(fsp)
                .setAutoFocusEnabled(true)
                .setRequestedPreviewSize(640, 480)
                .setFacing(camerId)
                .build()
    }

    companion object {
        const val DELAY_20_FPS = (100 * 2).toLong() // 200 milliseconds
        const val DELAY_15_FPS = (100 * 3).toLong() // 300 milliseconds
        const val DELAY_10_FPS = (100 * 4).toLong() // 400 milliseconds
        const val DELAY_5_FPS = (100 * 5).toLong() // 500 milliseconds

    }
}