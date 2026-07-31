package com.yourname.litelinkcompanion.commands

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Take a photo from the phone camera and push it to the laptop. */
object CameraModule {

    private const val TAG = "LiteLink.Camera"

    fun register(app: Context) {
        CommandCenter.register("camera.capture") { ctx, id, params ->
            try {
                capturePhoto(app, ctx, id, params.optString("dir", "camera"))
            } catch (e: Exception) {
                Log.e(TAG, "capture failed", e)
                ctx.fail(id, e.message ?: "capture failed")
            }
        }
    }

    private fun capturePhoto(app: Context, ctx: CommandContext, id: String, dir: String) {
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = findCamera(cm) ?: throw IllegalStateException("no camera available")
        val outDir = File(app.cacheDir, "camera").apply { mkdirs() }
        val outFile = File(outDir, "photo_${System.currentTimeMillis()}.jpg")

        val thread = HandlerThread("LiteLinkCamera").apply { start() }
        val handler = Handler(thread.looper)

        val reader = ImageReader.newInstance(1920, 1080, android.graphics.ImageFormat.JPEG, 2)
        reader.setOnImageAvailableListener({ r ->
            try {
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                outFile.writeBytes(bytes)
                CommandBridge.sendEvent("camera.photo", JSONObject().apply {
                    put("file", outFile.absolutePath)
                    put("size", bytes.size)
                })
                ctx.reply(id, JSONObject().put("status", "captured").put("size", bytes.size))
            } catch (e: Exception) {
                Log.e(TAG, "image read failed", e)
                ctx.fail(id, "image read failed")
            } finally {
                thread.quitSafely()
            }
        }, handler)

        @Suppress("MissingPermission")
        cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                try {
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    request.addTarget(reader.surface)
                    request.set(CaptureRequest.JPEG_QUALITY, 90)
                    val sessionCb = object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            try {
                                session.capture(request.build(), null, handler)
                            } catch (e: Exception) {
                                Log.e(TAG, "capture trigger failed", e)
                                camera.close()
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "configure failed")
                            camera.close()
                        }
                    }
                    camera.createCaptureSession(listOf(reader.surface), sessionCb, handler)
                } catch (e: Exception) {
                    Log.e(TAG, "setup failed", e)
                    camera.close()
                }
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }
            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "camera error $error")
                camera.close()
            }
        }, handler)
    }

    private fun findCamera(cm: CameraManager): String? {
        for (id in cm.cameraIdList) {
            val chars = cm.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return cm.cameraIdList.firstOrNull()
    }
}
