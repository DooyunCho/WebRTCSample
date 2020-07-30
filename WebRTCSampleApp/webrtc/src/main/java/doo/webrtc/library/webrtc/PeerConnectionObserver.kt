package doo.webrtc.library.webrtc

import android.os.Handler
import android.os.Looper
import doo.webrtc.library.media.MediaManager
import org.webrtc.*
import doo.webrtc.library.utils.Logger
import java.util.concurrent.ExecutorService

class PeerConnectionObserver(val executor: ExecutorService, val stateEventListener: PeerConnectionStateChangeListener) : PeerConnection.Observer {
    private val TAG = "PeerConnectionObserver"
    private var localSdp: SessionDescription? = null
    var peerConnection: PeerConnection? = null

    override fun onIceCandidate(candidate: IceCandidate?) {
        Logger.d(TAG, "onIceCandidate.candidate(): ${candidate.toString()}")
    }

    override fun onDataChannel(p0: DataChannel?) {}

    override fun onIceConnectionReceivingChange(p0: Boolean) {}

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
        Logger.d(TAG, "onIceGatheringChange: $newState")

        if (newState == PeerConnection.IceGatheringState.GATHERING) {
            Logger.d(TAG, "IceGatheringState.GATHERING")

            Handler(Looper.getMainLooper()).postDelayed({
                stateEventListener.onStateChanged(PeerConnectionState.STATE.LOCAL_READY, peerConnection?.localDescription)
            }, 3000)
        }
    }

    override fun onAddStream(remoteStream: MediaStream?) {
        Logger.d(TAG, "onAddStream")

        if (remoteStream == null) {
            Logger.w(TAG, "remoteStream is null.")
        } else {
            executor.execute{
                Logger.w(TAG, "remoteStream Ready.")

                if (remoteStream.audioTracks.size > 0) {

                }

                if (remoteStream.videoTracks.size > 0) {
                    // TODO: display Remote video.
//                    if (mImageView != null) {
//                        // Width, Height 계산
//                        val width = (mInstance.getWidth() * 0.25).toInt()
//                        val height = (width * 0.75).toInt()
//
//                        val rlp = RelativeLayout.LayoutParams(width, height)
//                        rlp.setMargins(70, 70, 70, 70)
//
//                        mImageView.setLayoutParams(rlp)
//
//                        mRelativeLayout.addView(mImageView)
//                        mInstance.addView(mRelativeLayout)
//                    } else {
//                        if (isDebug) Log.d(mTAG, "mRemoteVideoTrack add mRemoteVideoRenderer")
//                        mRemoteVideoTrack = remoteStream.videoTracks.get(0)
//                        mRemoteVideoTrack.addRenderer(MediaManager.getRemoteRenderer())
//                    }

                    remoteStream.videoTracks[0].addSink(MediaManager.getRenderer("Remote"))
                }

                stateEventListener.onStateChanged(PeerConnectionState.STATE.COMPLETE,peerConnection?.localDescription)
            }
        }
    }

    override fun onRemoveStream(p0: MediaStream?) {}

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

    override fun onRenegotiationNeeded() {}

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
}

