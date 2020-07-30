package doo.webrtc.library.interfaces

import org.json.JSONObject
import org.webrtc.SessionDescription

interface SignalingInterface {
    // request로 종료되는 시그널링은 Boolean을 반환함.

    fun connect(ip: String, port: String)
    fun ping()
    fun disconnect()
    fun register(dn: String, userData: JSONObject?)
    fun unregister(dn: String)
    fun invite(dn: String, ani: String, sdp: String)
    fun establish(): Boolean
    fun answer(callID: String, sdp: String)
    fun established(callID: String)
    fun trying()
    fun cancel(): Boolean
    fun reject()
    fun update()
    fun updated()
    fun bye()
    fun byeAck()
    fun recall(video: Boolean, audio: Boolean, sdp: String): Boolean
    fun reconnect(): Boolean
    fun sendMessage(callID: String, msg:String)
    fun refer(ani: String)
    fun ackNotify(event: String)
}