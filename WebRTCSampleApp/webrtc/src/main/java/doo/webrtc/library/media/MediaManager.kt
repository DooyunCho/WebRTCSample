package doo.webrtc.library.media

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import org.webrtc.*

object MediaManager {
    private val TAG = "MediaManager"
    private var remoteViews: HashMap<String, ViewGroup> = hashMapOf()

    fun makeAudioTrack(pcFactory: PeerConnectionFactory, mediaConstraints:MediaConstraints):AudioTrack {
        var audioSource = pcFactory.createAudioSource(mediaConstraints)
        return pcFactory.createAudioTrack("IPRONWebCall_AUDIO", audioSource)
    }

    fun makeVideoTrack(pcFactory: PeerConnectionFactory, mediaConstraints:MediaConstraints):AudioTrack {
        var audioSource = pcFactory.createAudioSource(mediaConstraints)
        return pcFactory.createAudioTrack("IPRONWebCall_AUDIO", audioSource)
    }

    // Create VideoCapturer
    public fun createVideoCapturer(context:Context): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        Logging.d(TAG, "Creating capturer using camera2 API.")
        videoCapturer = createCameraCapturer(
            Camera2Enumerator(context)
        )
        if (videoCapturer == null) {
            Log.e(TAG, "Failed to open camera")
            return null
        }
        return videoCapturer
    }

    // Create VideoCapturer from camera
    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.")
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer:$deviceName")
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.")
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.")
                val videoCapturer = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        return null
    }

    fun makeRenderer(key: String, parentView: ViewGroup, eglBase: EglBase, mirror: Boolean): SurfaceViewRenderer {
        var view = SurfaceViewRenderer(parentView.context)
        view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        view.init(eglBase.getEglBaseContext(), null)
        view.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        view.setMirror(mirror)

        remoteViews.put(key, parentView)

        return view
    }

    fun getRenderer(key: String): SurfaceViewRenderer? {
        if (remoteViews[key] == null) return null

        return remoteViews[key]!!.getChildAt(0) as SurfaceViewRenderer
    }

    fun clearRenderer() {
        for (view in remoteViews) {
            (view.value.getChildAt(0) as SurfaceViewRenderer).clearImage()
            (view.value.getChildAt(0) as SurfaceViewRenderer).release()

            Handler(Looper.getMainLooper()).post {
                view.value.removeAllViews()
            }
        }

        remoteViews.clear()
    }










//    private class ProxyRenderer : VideoRenderer.Callbacks {
//        private var target: VideoRenderer.Callbacks? = null
//
//        @Synchronized
//        override fun renderFrame(frame: VideoRenderer.I420Frame) {
//            if (target == null) {
//                Logging.d(TAG, "Dropping frame in proxy because target is null.")
//                VideoRenderer.renderFrameDone(frame)
//                return
//            }
//
//            target!!.renderFrame(frame)
//        }
//
//        @Synchronized
//        fun setTarget(target: VideoRenderer.Callbacks) {
//            this.target = target
//        }
//    }
//
//    private class ProxyVideoSink : VideoSink {
//        private var target: VideoSink? = null
//
//        @Synchronized
//        override fun onFrame(frame: VideoFrame) {
//            if (target == null) {
//                Logging.d(TAG, "ProxyVideoSink: Dropping frame in proxy because target is null.")
//                return
//            }
//            target!!.onFrame(frame)
//        }
//
//        @Synchronized
//        fun setTarget(target: VideoSink) {
//            this.target = target
//        }
//    }
}