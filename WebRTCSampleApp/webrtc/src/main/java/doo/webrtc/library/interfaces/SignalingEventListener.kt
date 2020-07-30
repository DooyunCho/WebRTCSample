package doo.webrtc.library.interfaces

interface SignalingEventListener {
    fun onConnected()
    fun onPong()
    fun onRegistered()
    fun onUnregistered()
    fun onInvited(msg: String)
    fun onTrying(msg: String)
    fun onPreset(sdp: String)
    fun onAnswered(ani: String, msg: String)
    fun onEstablished(msg: String) // 수신 완료시
    fun onEstablished() // 발신 완료시
    fun onBye()
    fun onByeAck()
    fun onReject(code: Int, text: String)
    fun onUpdated()
    fun onCancel()
    fun onRecall()
    fun onReconnected()
    fun onNotify()
    fun onMessage()
    fun onDisconnected()
    fun onError() // websoket error
}