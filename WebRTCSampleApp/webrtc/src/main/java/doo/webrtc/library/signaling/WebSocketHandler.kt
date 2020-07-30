package doo.webrtc.library.signaling

import android.os.Handler
import doo.webrtc.library.interfaces.SignalingEventListener
import doo.webrtc.library.interfaces.SignalingInterface
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import doo.webrtc.library.utils.Logger
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.protocols.Protocol
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.security.SecureRandom
import javax.net.ssl.*
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.random.Random


class WebSocketHandler(val signalingEventListener: SignalingEventListener):
    SignalingInterface {
    private val TAG = "WebSocketHandler"
    private val WCP_VERSION = "WCP/2.2"
    private val SUB_PROTOCOL_KEY = "Sec-WebSocket-Protocol"
    private val SUB_PROTOCOL = "wcp"
    private val SUB_PROTOCOL_INPUT_MAX_SIZE = 0x10000
    private var CALL_ID = ""
    private var TOKEN_ID: String = ""
    private var OWMS_ID: String = ""
    private var USER_DATA: String = ""

    lateinit var webSocket: WebSocketClient

    /***************************** Timer *********************************/
    private var pingHandler: Handler = Handler()
    private val pingRunnable: Runnable = Runnable {
        this@WebSocketHandler.ping()
    }
    private var pingTimeout = 5000L

    private var updateHandler: Handler = Handler()
    private val reqUpdateRunnable: Runnable = Runnable {
        this@WebSocketHandler.update()
    }
    private var updateTimeout = 30000L
    /****************************************************************************/
    var signalingState = SignalingState.STATE.IDLE
    /****************************************************************************/


    override fun ping() {
        val pingString = "{\"type\":\"req-ws-ping\",\"version\":\"$WCP_VERSION\",\"token_id\":$TOKEN_ID}"
        send(pingString)
    }

    override fun disconnect() {
        if (webSocket.isOpen) webSocket.close()
        else Logger.d(TAG, "Already close webSocket.")
    }

    override fun register(dn: String, userData: JSONObject?) {
        if (signalingState == SignalingState.STATE.INIT) {
            var userDataString = ""

            if (userData == null) {
                // DummyData
                userDataString = ""
            } else {
                userDataString = userData.toString()
            }

            USER_DATA = userDataString
            CALL_ID = guid()
            val registerString = ""

            send(registerString)
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-register]. Current state: $signalingState")
        }
    }

    override fun unregister(dn: String) {
        if (signalingState == SignalingState.STATE.REGISTERED) {
            val unregisterString = ""
            send(unregisterString)
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-unregister]. Current state: $signalingState")
        }
    }

    override fun invite(dn: String, ani: String, sdp: String) {
        if (signalingState == SignalingState.STATE.INIT || signalingState == SignalingState.STATE.REGISTERED) {
            CALL_ID = guid()
            var callUserData = ""
            var callUUID = "\"\""
            val intiveString = ""

            Logger.d(TAG, intiveString)

            send(JSONObject(intiveString).toString())
        } else if(signalingState == SignalingState.STATE.DIALING || signalingState == SignalingState.STATE.PRESET) {
            Logger.w(TAG, "[WARING] Cancel Call []. Current state: $signalingState")

            if (cancel()) {
                invite(dn, ani, sdp)
            }
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-newcall]. Current state: $signalingState")
        }
    }

    override fun establish(): Boolean {
        if (signalingState == SignalingState.STATE.INIT || signalingState == SignalingState.STATE.DIALING || signalingState == SignalingState.STATE.PRESET) {
            var tryingString = ""
            if (send(tryingString)){
                signalingState =
                    SignalingState.STATE.ESTABLISHED
                signalingEventListener.onEstablished()
                updateHandler.postDelayed(reqUpdateRunnable, updateTimeout)
                return true
            }
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-connect]. Current state: $signalingState")
            return false
        }

        return false
    }

    override fun trying() {
        if (signalingState == SignalingState.STATE.RINGING) {
            var tryingString = ""
            if (send(tryingString)){

            }
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-accept]. Current state: $signalingState")
        }
    }

    override fun answer(callID: String, sdp: String) {
        if (signalingState == SignalingState.STATE.RINGING) {
            var answerString = ""
            send(JSONObject(answerString).toString())
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-answer]. Current state: $signalingState")
        }
    }

    override fun established(callID: String) {
    }

    override fun refer(ani: String) {
        if (signalingState == SignalingState.STATE.ESTABLISHED) {
            var referString = ""
            var result = send(referString)

        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-refer]. Current state: $signalingState")
        }
    }

    override fun cancel(): Boolean {
        if (signalingState == SignalingState.STATE.DIALING || signalingState == SignalingState.STATE.PRESET) {
            var cancelString = ""
            var result = send(cancelString)

            if (result) signalingState =
                SignalingState.STATE.REGISTERED
            return result
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-cancel]. Current state: $signalingState")
        }

        return false
    }

    override fun reject() {
        if (signalingState == SignalingState.STATE.RINGING) {
            var rejectString = ""
            if (send(rejectString)) {
                signalingState =
                    SignalingState.STATE.REGISTERED
            }
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-reject]. Current state: $signalingState")
        }
    }

    override fun update() {
        val updateString = ""
        send(updateString)
    }

    override fun updated() {
        val updatedString = ""
        send(updatedString)
    }

    override fun ackNotify(event: String) {
        val ackNotifyString = ""
        send(ackNotifyString)
    }

    override fun bye() {
        if (signalingState == SignalingState.STATE.ESTABLISHED) {
            var byeString = ""
            var result = send(byeString)

            if (result) signalingState =
                SignalingState.STATE.REGISTERED
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-endcall]. Current state: $signalingState")
        }
    }

    override fun byeAck() {
        if (signalingState == SignalingState.STATE.REGISTERED || signalingState == SignalingState.STATE.INIT) {
            var byeString = ""
            send(byeString)
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-disconnect]. Current state: $signalingState")
        }
    }

    override fun recall(video: Boolean, audio: Boolean, sdp: String): Boolean {
        var recallString = ""

        if (signalingState == SignalingState.STATE.ESTABLISHED || signalingState == SignalingState.STATE.REGISTERED) { // hold and unhold
            if (sdp.isEmpty()) {
                var mattrDataString = ""
                val mattrObject = JSONObject()
                try {
                    val audioMode = JSONObject()
                    audioMode.put("mode", if (audio) "sendonly" else "sendrecv")
                    val videoMode = JSONObject()
                    videoMode.put("mode", if (video) "inactive" else "sendrecv")
                    mattrObject.put("audio", audioMode)
                    mattrObject.put("video", videoMode)
                } catch (var5: Exception) {

                }
                mattrDataString = mattrObject.toString()
                recallString = ""
                return send(JSONObject(recallString).toString())
            }
        } else if (signalingState == SignalingState.STATE.INIT) { // wifi/lte swap reconnect
            if (sdp.isEmpty().not()) {
                recallString = ""
                return send(JSONObject(recallString).toString())
            }
        } else {
            Logger.e(TAG, "[ERROR] fail to send [req-recall]. Current state: $signalingState")
        }

        return false
    }

    override fun reconnect(): Boolean {

        var reconnectString = ""
        if (send(reconnectString)) {
            signalingState =
                SignalingState.STATE.ESTABLISHED
            return true
        } else {
            return false
        }
    }

    override fun sendMessage(callID: String, msg: String) {
    }


    /********************************************************************************
     *                            Private Method                                    *
     *********************************************************************************/
    override fun connect(ip: String, port: String) {
        Logger.d(TAG, "connect: $ip, $port")

        if (signalingState == SignalingState.STATE.IDLE || signalingState == SignalingState.STATE.WIFILTESWAP) {
            webSocket = makeWebSocket(ip, port)
            webSocket.connect()
        } else {
            Logger.e(TAG, "[ERROR] fail to send [connect]. Current state: $signalingState")
        }
    }

    private fun guid(): String {
        val uuid = UUID.randomUUID()
        return uuid.toString().toUpperCase()

//        return s4() + s4() + '-' + s4() + '-' + s4() + '-' +s4() + '-' + s4() + s4() + s4()
    }

    private fun s4(): String {
        return String.format("%02X", Random.nextLong().toByte()) + String.format("%02X", Random.nextLong().toByte())
    }

    private fun makeWebSocket(ip: String, port: String): WebSocketClient {
        var uri = URI("wss://$ip:$port/")
        val headers = mapOf(Pair(SUB_PROTOCOL_KEY, SUB_PROTOCOL))
        Logger.d(TAG, "connect: ${uri}")

        var localWebSocket = object : WebSocketClient(uri, Draft_6455(listOf(), listOf(Protocol(SUB_PROTOCOL)), SUB_PROTOCOL_INPUT_MAX_SIZE), headers) {
            override fun onOpen(serverHandshake: ServerHandshake) {
                Logger.d(TAG, "Opened websocket: $ip, $port")
                this@WebSocketHandler.Init()
            }

            override fun onMessage(s: String) {
                Logger.d(TAG, "onMessage: $s")

                try {
                    var json = JSONObject(s)

                    if (json.getString("type").equals("event-ws-init")) {
                        TOKEN_ID = json.getString("token_id")
                        if(json.has("owms_id")) OWMS_ID = json.getString("owms_id")
                        if(json.has("ka_interval")) pingTimeout = json.getInt("ka_interval") * 1000L

                        signalingState =
                            SignalingState.STATE.INIT
                        signalingEventListener.onConnected()

                        pingHandler.postDelayed(pingRunnable, pingTimeout)
                    } else if (json.getString("type").equals("event-registered")) {
                        signalingState =
                            SignalingState.STATE.REGISTERED
                        signalingEventListener.onRegistered()
                    } else if (json.getString("type").equals("event-unregistered")) {
                        signalingState =
                            SignalingState.STATE.INIT
                        signalingEventListener.onUnregistered()
                    } else if (json.getString("type").equals("event-newcall")) {
                        if(json.has("call_id")) CALL_ID = json.getString("call_id")
                        signalingState =
                            SignalingState.STATE.RINGING
                        trying()
                        signalingEventListener.onInvited(s)
                    } else if (json.getString("type").equals("event-preset")) {
                        if (json.has("sdp")) {
                            signalingState =
                                SignalingState.STATE.PRESET
                            signalingEventListener.onPreset(json.getString("sdp"))
                        } else {
                            // TODO: 예외처리
                        }
                    } else if (json.getString("type").equals("event-accepted")) {
                        signalingState =
                            SignalingState.STATE.DIALING
                        signalingEventListener.onTrying(s)
                    } else if (json.getString("type").equals("event-answered")) {
                        if(json.has("owms_id")) OWMS_ID = json.getString("owms_id")
                        signalingEventListener.onAnswered("", json.toString())
                    } else if (json.getString("type").equals("event-connected")) {
                        if (json.has("sdp")) {
                            signalingState =
                                SignalingState.STATE.ESTABLISHED
                            signalingEventListener.onEstablished(json.getString("sdp"))
                        } else {
                            // TODO: 예외처리
                        }
                    } else if (json.getString("type").equals("event-disconnected")) {
                        // TODO: Register / Init 상태인지 확인하여 해당 상태로 돌려야함
                        signalingState =
                            SignalingState.STATE.REGISTERED
                        signalingEventListener.onByeAck()
                    } else if (json.getString("type").equals("event-rejected")) {
                        var code = json.getJSONObject("reason").getInt("code")
                        var text = json.getJSONObject("reason").getString("text")
                        Logger.e(TAG, "onReject: $code, $text, $signalingState")

                        if (code == 486 || code == 404) {   // 상대방의 통화 거절
                            // TODO: Register / Init 상태인지 확인하여 해당 상태로 돌려야함
                            signalingState =
                                SignalingState.STATE.REGISTERED
                            signalingEventListener.onReject(code, text)
                        } else if (code == 503) {    // Register 실패
                            signalingState =
                                SignalingState.STATE.INIT
                            signalingEventListener.onReject(code, text)
                        } else {    // Register 실패
                            // TODO: Register / Init 상태인지 확인하여 해당 상태로 돌려야함
                            signalingState =
                                SignalingState.STATE.INIT
                            signalingEventListener.onReject(code, text)
                        }
                    } else if (json.getString("type").equals("event-cancel")) {
                        // TODO: Register / Init 상태인지 확인하여 해당 상태로 돌려야함
                        signalingState =
                            SignalingState.STATE.REGISTERED
                        signalingEventListener.onCancel()
                    } else if (json.getString("type").equals("event-endcall")) {
                        // TODO: Register / Init 상태인지 확인하여 해당 상태로 돌려야함
                        signalingState =
                            SignalingState.STATE.REGISTERED
                        signalingEventListener.onBye()
                    } else if (json.getString("type").equals("event-ws-pong")) {
                        pingHandler.postDelayed(pingRunnable, pingTimeout)
                    } else if (json.getString("type").equals("event-updated")) {
                        updateHandler.postDelayed(reqUpdateRunnable, updateTimeout)
                    } else if (json.getString("type").equals("req-update")) {
                        updated()
                    } else if (json.getString("type").equals("event-notify")) {
                        ackNotify(json.getString("event"))
                    } else if (json.getString("type").equals("event-recall")) {
                        if (!json.has("sdp") && json.has("mattr")) run {
                            Logger.d(TAG, "recall: hold")

                            var audio = ""
                            var video = ""
                            var bitrate_video = -1
                            var bitrate_audio = -1

                            val mattrJSON = json.getJSONObject("mattr")

                            if (!mattrJSON.has("audio")) {
                                audio = "sendrecv"
                                Logger.d(TAG, "Not have audio")
                            } else {
                                val audioJSON = mattrJSON.getJSONObject("audio")

                                if (!audioJSON.has("mode")) {
                                    audio = "sendrecv"
                                } else if (audioJSON.getString("mode") == "sendonly") {
                                    audio = "recvonly"
                                } else if (audioJSON.getString("mode") == "sendrecv") {
                                    audio = "sendrecv"
                                }

                                if (audioJSON.has("bandwidth")) {
                                    val bandwidth = audioJSON.getInt("bandwidth")
                                    audio = "sendrecv"
                                    bitrate_audio = bandwidth
                                    Logger.d(TAG, "bitrate_audio: $bitrate_audio")
                                }
                            }

                            if (!mattrJSON.has("video")) {
                                video = "sendrecv"
                                Logger.d(TAG, "Not have video")
                            } else {
                                val videoJSON = mattrJSON.getJSONObject("video")

                                if (!videoJSON.has("mode")) {
                                    video = "sendrecv"
                                } else if (videoJSON.getString("mode") == "sendonly") {
                                    video = "recvonly"
                                } else if (videoJSON.getString("mode") == "sendrecv") {
                                    video = "sendrecv"
                                }

                                if (videoJSON.has("bandwidth")) {
                                    val bandwidth = videoJSON.getInt("bandwidth")
                                    video = "sendrecv"
                                    bitrate_video = bandwidth
                                    Logger.d(TAG, "bitrate_video: $bitrate_video")
                                }
                            }

                            var recallString = ""
                            send(recallString)
                        }
                    }
                } catch (e: JSONException) {
                    Logger.e(TAG, "JSONException")
                    e.printStackTrace()
                }
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                Logger.d(TAG, "onClose: $code, $reason, $remote")
                if (signalingState == SignalingState.STATE.ESTABLISHED || signalingState == SignalingState.STATE.WIFILTESWAP) {
                    signalingState =
                        SignalingState.STATE.WIFILTESWAP
                } else {
                    signalingState =
                        SignalingState.STATE.IDLE
                }
                signalingEventListener.onDisconnected()
                pingHandler.removeCallbacks(pingRunnable)
            }

            override fun onError(e: Exception) {
                Logger.d(TAG, "onError: ${e.message}")
                signalingEventListener.onError()
                pingHandler.removeCallbacks(pingRunnable)
                e.printStackTrace()
            }
        }

        // All Trust true
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(p0: Array<out java.security.cert.X509Certificate>?, p1: String?) {
            }

            override fun checkServerTrusted(p0: Array<out java.security.cert.X509Certificate>?, p1: String?) {
                Logger.d(TAG, "checkServerTrustedonClose: $p1")
            }

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate?> {
                return arrayOfNulls(0)
            }
        })

        var sslContext: SSLContext? = null
        try {
            sslContext = SSLContext.getInstance("SSL")
            sslContext!!.init(
                null,
                trustAllCerts,
                SecureRandom()
            )

            var factory = sslContext.socketFactory
            localWebSocket.setSocketFactory(factory)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: KeyManagementException) {
            e.printStackTrace()
        }

        return localWebSocket
    }

    private fun send(str: String): Boolean {
        if (webSocket.isOpen) {
            Logger.d(TAG, "Send Message: $str")
            webSocket.send(str)
            return true
        } else {
            Logger.e(TAG, "Fail to Send Message: $str")
            return false
        }
    }

    private fun Init() {
        var initString = ""

        if (signalingState == SignalingState.STATE.WIFILTESWAP && TOKEN_ID.isEmpty().not()) {
            initString = ""
        } else {
            initString = ""
        }

        send(initString)
    }


}