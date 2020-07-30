package doo.webrtc.library.webrtc

import android.content.Context
import doo.webrtc.library.WebRTCLibrary
import doo.webrtc.library.PeerConnectionParameter
import doo.webrtc.library.media.MediaManager
import org.webrtc.*
import java.util.concurrent.Executors
import doo.webrtc.library.utils.Logger

class PeerConnectionHandler(
    val context: Context,
    val peerConnectionFactory: PeerConnectionFactory,
    val eglBase: EglBase,
    val iceServers: MutableList<PeerConnection.IceServer>,
    val peerConnectionParameter: PeerConnectionParameter,
    val stateChangeListener: PeerConnectionStateChangeListener
) {
    private val TAG = "PeerConnectionHandler"

    private var peerConnection: PeerConnection? = null
    lateinit var videoCapturer: VideoCapturer
    lateinit var videoSource: VideoSource
    private var audioSource: AudioSource? = null
    lateinit var localVideoTrack: VideoTrack
    lateinit var localAudioTrack: AudioTrack
    lateinit var mediaConstraints: MediaConstraints

    lateinit private var sdpObserver: SDPObserver

    private var audioEnabled: Boolean = false
    private var videoEnabled: Boolean = false
    private var width: Int = 640
    private var height: Int = 480
    private val fps: Int = 30

    private val executor = Executors.newSingleThreadExecutor()

    /****************************************************************************/
    var localReady = false
    var remoteReady = false
    /****************************************************************************/

    init {
        // AUDIO / VIDEO 설정
        when (peerConnectionParameter.mediaOption) {
            WebRTCLibrary.MEDIA_OPTION.AUDIO -> audioEnabled = true
            WebRTCLibrary.MEDIA_OPTION.VIDEO -> videoEnabled = true
            WebRTCLibrary.MEDIA_OPTION.AUDIO_VIDEO -> {
                audioEnabled = true
                videoEnabled = true
            }
        }

        Logger.d(TAG, "Media Option: audio=$audioEnabled, video=$videoEnabled")

        // VIDEO 해상도 설정
        when (peerConnectionParameter.resolution) {
            WebRTCLibrary.RESOLUTION.VGA -> {
                width = 480
                height = 640
            }
            WebRTCLibrary.RESOLUTION.QVGA -> {
                width = 240
                height = 320
            }
        }

        Logger.d(TAG, "Resolution: width=$width, height=$height")

        mediaConstraints = MediaConstraints()

        mediaConstraints.optional.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveAudio",
                if (audioEnabled) "true" else "false"
            )
        )
        mediaConstraints.optional.add(
            MediaConstraints.KeyValuePair(
                "OfferToReceiveVideo",
                if (videoEnabled) "true" else "false"
            )
        )
        mediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

        if (videoEnabled) {
            peerConnectionFactory.setVideoHwAccelerationOptions(
                eglBase.getEglBaseContext(),
                eglBase.getEglBaseContext()
            )
        } else {
            Logger.d(TAG, "Skip setVideoHwAccelerationOptions")
        }

        createPeerConnection()
        Logger.d(TAG, "create PeerConnection")
        sdpObserver = SDPObserver(executor, peerConnection!!)
        Logger.d(TAG, "create sdpObserver")
    }

    /********************************************************************************
     *                                  Public method                               *
     ********************************************************************************/
    fun createOffer() {
        val mediaStream = createLocalStream()

        if (peerConnection == null) {
            return
        }

        Logger.d(TAG, "addStream: $mediaStream, ${peerConnection!!.nativePeerConnection}")
        peerConnection!!.addStream(mediaStream)
        Logger.d(TAG, "peerConnection.addStream")
        peerConnection!!.createOffer(getSdpObserver(), mediaConstraints)
        Logger.d(TAG, "peerConnection.createOffer")
    }

    fun createAnswer() {
        val mediaStream = createLocalStream()

        if (peerConnection == null) {
            return
        }

        peerConnection!!.addStream(mediaStream)
        peerConnection!!.createOffer(getSdpObserver(), mediaConstraints)
    }

    fun setRemoteDescription(sdp: String) {
        Logger.e(TAG, "setRemoteDescription")
        peerConnection!!.signalingState()
        var sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection!!.setRemoteDescription(getSdpObserver(), sessionDescription)
    }

    fun sendDTMF(dtmf: String, duration: Int, gap: Int): Boolean {
        var durationVariable = duration
        var gapVariable = gap

        if (peerConnection!!.senders.isNotEmpty()) {
            if (durationVariable < 40) {
                durationVariable = 100
            }
            if (gapVariable < 40) {
                gapVariable = 70
            }

            var rtpSender = peerConnection!!.senders[0]

            return rtpSender.dtmf().insertDtmf(dtmf, durationVariable, gapVariable)
        }

        return false
    }

    fun clear() {
        if (peerConnection != null) {
            peerConnection!!.dispose()
            peerConnection = null
        }

        if (audioEnabled) {
            if (audioSource != null) {
                audioSource!!.dispose()
                audioSource = null
            }
        }

        if (videoEnabled) {
            if (videoCapturer != null) {
                try {
                    videoCapturer.stopCapture()
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
                videoCapturer.dispose()
            }

            if (videoSource != null) {
                videoSource.dispose()
            }
        }
    }


    /********************************************************************************
     *                                  Private method                              *
     ********************************************************************************/

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
//        rtcConfig.disableIPv6OnWifi = true
//        rtcConfig.disableIpv6 = true

        val peerConnectionObserver = PeerConnectionObserver(
            executor,
            stateChangeListener
        )
        Logger.d(TAG, "create peerConnectionObserver")
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, mediaConstraints, peerConnectionObserver)
        Logger.d(TAG, "create peerConnection: ${peerConnection!!.nativePeerConnection}")
        peerConnectionObserver.peerConnection = peerConnection
    }

    private fun createLocalStream(): MediaStream {
        val mediaStream = peerConnectionFactory.createLocalMediaStream("IPRONWebCall")
        val videoKey = MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        val audioKey = MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")

        if (mediaConstraints.optional.contains(videoKey)) {
            Logger.d(TAG, "Create local video track.")
            videoCapturer = MediaManager.createVideoCapturer(context)!!
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer)
            videoSource.adaptOutputFormat(width, height, fps)
            videoCapturer.startCapture(width, height, fps)

            localVideoTrack = peerConnectionFactory.createVideoTrack("IPRONWebCall_VIDEO", videoSource)
            localVideoTrack.setEnabled(true)
            localVideoTrack.addSink(MediaManager.getRenderer("Local"))

            mediaStream.addTrack(localVideoTrack)
        }

        if (mediaConstraints.optional.contains(audioKey)) {
            Logger.d(TAG, "Create local audio track.")
            audioSource = peerConnectionFactory.createAudioSource(mediaConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack("IPRONWebCall_AUDIO", audioSource)
            mediaStream.addTrack(localAudioTrack)
        }

        Logger.d(TAG, "Create local media stream.")

        return mediaStream
    }

    private fun getSdpObserver(): SDPObserver {
        if (sdpObserver == null) {
            sdpObserver =
                SDPObserver(executor, peerConnection!!)
        }

        Logger.d(TAG, "getSdpObserver: ${sdpObserver.toString()}")
        return sdpObserver
    }
}