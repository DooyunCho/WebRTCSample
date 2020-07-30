package doo.webrtc.library.signaling

interface SignalingStateChangeListener {
    fun onStateChanged(lastState: SignalingState.STATE, currentState: SignalingState.STATE)
}