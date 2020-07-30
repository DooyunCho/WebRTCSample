package doo.webrtc.library.webrtc

import org.webrtc.SessionDescription

interface PeerConnectionStateChangeListener {
    fun onStateChanged(
        state: PeerConnectionState.STATE,
        sdp: SessionDescription?
    )
}