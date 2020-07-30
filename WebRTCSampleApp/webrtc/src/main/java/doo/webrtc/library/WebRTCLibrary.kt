package doo.webrtc.library

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.ViewGroup
import doo.webrtc.library.interfaces.SignalingEventListener
import doo.webrtc.library.media.MediaManager
import doo.webrtc.library.signaling.SignalingState
import doo.webrtc.library.signaling.WebSocketHandler
import doo.webrtc.library.utils.Defines
import doo.webrtc.library.utils.Logger
import doo.webrtc.library.webrtc.PeerConnectionHandler
import doo.webrtc.library.webrtc.PeerConnectionState
import doo.webrtc.library.webrtc.PeerConnectionStateChangeListener
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import java.util.*

class WebRTCLibrary(
    val context: Context,
    val eventListener: SignalingEventListener,
    val peerConnectionParameter: PeerConnectionParameter,
    val signalingParameter: SignalingParameter
) : PeerConnectionStateChangeListener,
    SignalingEventListener {
    val TAG = "WebRTCLibrary"

    private var myDN: String = ""
    private var ani: String = ""
    private var isHold = false
    private var isReconnect = false
    private var IP: String = ""
    private var PORT: String = ""

    /******** Constant ********/
    enum class RESOLUTION { QVGA, VGA }

    enum class MEDIA_OPTION { AUDIO, VIDEO, AUDIO_VIDEO }
    enum class CAMERA_OPTION { FRONT, REAR, NONE }
    enum class VIEW_MODE { SEE_ME, SEE_YOU }

    /******** WebRTC ********/
    var peerConnectionFactory: PeerConnectionFactory
    lateinit var peerConnectionHandler: PeerConnectionHandler
    val eglBase: EglBase = EglBase.create()

    /******** OWMS ********/
    lateinit var webSocketHandler: WebSocketHandler

    /******** Queue ********/
    var queue = LinkedList<OnCompleteListener>()

    /******** Timeout ********/
    private val requestTimeout = 10000L
    private val timeoutHandler = Handler()
    private val wifiLteSwapHandler = Handler()
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private var timerCount = 0
    private var lastCompleteListener: OnCompleteListener? = null
    private var timeoutRunnable = Runnable {
        Logger.e(TAG, "NOT RESPONSE REQUEST 10sec.")
        complete(RESULT.TIMEOUT, "")
    }

    private var wifiLteSwapRunnable = Runnable {
        Logger.e(TAG, "Websocket error: retry connect")
        isReconnect = true
        webSocketHandler.connect(IP, PORT)
    }

    init {
        Logger.r(TAG, "**********************************************")
        Logger.r(TAG, "* AppPackage     : ${context.packageName}")
        Logger.r(TAG, "* Lib_VersionCode: ${BuildConfig.VERSION_CODE}")
        Logger.r(TAG, "* Lib_VersionName: ${BuildConfig.VERSION_NAME}")
        Logger.r(TAG, "* WebRTCVersion  : ${BuildConfig.WebRTCVersion}")
        Logger.r(TAG, "**********************************************")

        peerConnectionFactory = createPeerConnectionFactory()

        webSocketHandler = WebSocketHandler(this)
    }

    fun clear() {
        MediaManager.clearRenderer()
        peerConnectionHandler.clear()
        ani = ""
        isReconnect = false
        isHold = false
    }


    /******** CompleteListener ********/
    enum class RESULT { SUCCESS, FAIL, NOT_FINISH, TIMEOUT }

    interface OnCompleteListener {
        fun onComplete(result: RESULT, msg: String)
    }

    private fun complete(result: RESULT, msg: String): Boolean {
        Logger.d(TAG, "onCompleteListener queue size: ${queue.size}")

        if (!queue.isEmpty()) {
            var onCompleteListener = queue.first

            if (onCompleteListener.equals(lastCompleteListener)) {
                Logger.d(TAG, "Stop timeout handler.")
                timeoutHandler.removeCallbacks(timeoutRunnable)
                lastCompleteListener = null
            }

            onCompleteListener.onComplete(result, msg)
            return queue.remove(onCompleteListener)
        }

        return false
    }

    fun setCompleteListener(onCompleteListener: OnCompleteListener): Boolean {
        var result = queue.add(onCompleteListener)

        if (result) {
            Logger.d(TAG, "Success to set Listener")
            lastCompleteListener = onCompleteListener
            timeoutHandler.postDelayed(timeoutRunnable, requestTimeout)
        } else {
            Logger.d(TAG, "Fail to set Listener")
        }

        return result
    }


    /******** Private Method ********/
    private fun makeIceServers(list: List<String>): MutableList<PeerConnection.IceServer> {
        Logger.d(TAG, "makeIceServers: ${list}")
        var iceServers = mutableListOf<PeerConnection.IceServer>()

        try {
            if (list.size > 0) {
                for (i in 0 until list.size) {
                    val serverInfoStr = list.get(i)
//                    var splitStr = serverInfoStr.split("/")
                    var splitJson = JSONObject(serverInfoStr)

                    if (splitJson.length() > 0) {
                        var url = splitJson.getString("url")

                        if (url.startsWith("stun:")) {           // STUN
                            iceServers.add(PeerConnection.IceServer.builder(url).createIceServer())
                        } else if (url.startsWith("turn:")) {     // TURN
                            val userName = splitJson.getString("username")
                            val credential = splitJson.getString("credential")

                            if (url.startsWith("turn:")) {
                                val builder = PeerConnection.IceServer.builder(url)
                                builder.setUsername(userName)
                                builder.setPassword(credential)

                                iceServers.add(builder.createIceServer())
                            }
                        }
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        return iceServers
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setFieldTrials("WebRTC-IntelVP8/Enabled/VideoFrameEmit/Enabled/")
            .setEnableVideoHwAcceleration(true)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext, true /* enableIntelVp8Encoder */, false
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.getEglBaseContext())

        return PeerConnectionFactory(PeerConnectionFactory.Options(), encoderFactory, decoderFactory)
    }

    private fun checkMediaOption(userData: JSONObject?): JSONObject? {
        var myUserData = userData

        // VIDEO/AUDIO 설정이 없을 경우 삽입
        if (myUserData == null) {
            myUserData = JSONObject()

            if (!myUserData.has("mattr")) {
                var mattr = JSONObject()
                val video =
                    peerConnectionParameter.mediaOption == MEDIA_OPTION.VIDEO || peerConnectionParameter.mediaOption == MEDIA_OPTION.AUDIO_VIDEO
                val audio =
                    peerConnectionParameter.mediaOption == MEDIA_OPTION.AUDIO || peerConnectionParameter.mediaOption == MEDIA_OPTION.AUDIO_VIDEO
                mattr.put("video", JSONObject("{\"allow\":$video}"))
                mattr.put("audio", JSONObject("{\"allow\":$audio}"))

                myUserData.put("mattr", mattr)
            }
        } else if (!myUserData.has("mattr")) {
            var mattr = JSONObject()
            val video =
                peerConnectionParameter.mediaOption == MEDIA_OPTION.VIDEO || peerConnectionParameter.mediaOption == MEDIA_OPTION.AUDIO_VIDEO
            val audio =
                peerConnectionParameter.mediaOption == MEDIA_OPTION.AUDIO || peerConnectionParameter.mediaOption == MEDIA_OPTION.AUDIO_VIDEO
            mattr.put("video", JSONObject("{\"allow\":$video}"))
            mattr.put("audio", JSONObject("{\"allow\":$audio}"))

            myUserData.put("mattr", mattr)
        }

        return myUserData
    }

    fun startTimer() {
        Logger.d(TAG, "startTimer ")
        timer = Timer(false)
        timerTask = object : TimerTask() {
            override fun run() {
                timerCount++
            }
        }
        timer!!.schedule(timerTask, 0, 1000)
    }

    fun stopTimer() {
        Logger.d(TAG, "stopTimer")
        if (timer != null) {
            timerCount = 0
            timer!!.cancel()
        }
    }

    /********************************************************************************
     *                                      APIs                                    *
     *********************************************************************************/
    fun connect(ip: String, port: String, onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        if (ip.isEmpty().not()) {
            IP = ip
        }
        if (port.isEmpty().not()) {
            PORT = port
        }

        webSocketHandler.connect(IP, PORT)
    }

    fun disconnect() {
        if (webSocketHandler.signalingState != SignalingState.STATE.IDLE) {
            webSocketHandler.disconnect()
        }
    }

    fun invite(
        from: String,
        to: String,
        localView: ViewGroup,
        remoteView: ViewGroup,
        onCompleteListener: OnCompleteListener
    ) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }
        Logger.d(TAG, "Start invite")

        if (myDN == null) {
            myDN = from
        }

        ani = to

        var localRenderer: SurfaceViewRenderer? = null
        var remoteRenderer: SurfaceViewRenderer? = null

        if (remoteView != null) {
            localRenderer = MediaManager.makeRenderer("Local", localView, eglBase, true)
            remoteRenderer = MediaManager.makeRenderer("Remote", remoteView, eglBase, true)
            localView.addView(localRenderer)
            remoteView.addView(remoteRenderer)
        }

        peerConnectionHandler = PeerConnectionHandler(
            context,
            peerConnectionFactory,
            eglBase,
            makeIceServers(signalingParameter.iceServers),
            peerConnectionParameter,
            this
        )
        Logger.d(TAG, "Create PeerConnectionHandler")
        peerConnectionHandler.createOffer()
    }

    fun invite(
        from: String,
        to: String,
        onCompleteListener: OnCompleteListener
    ) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        if (myDN == null) {
            myDN = from
        }

        ani = to

        peerConnectionHandler = PeerConnectionHandler(
            context,
            peerConnectionFactory,
            eglBase,
            makeIceServers(signalingParameter.iceServers),
            peerConnectionParameter,
            this
        )
        peerConnectionHandler.createOffer()
    }

    fun answer(localView: ViewGroup, remoteView: ViewGroup, onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        var localRenderer: SurfaceViewRenderer? = null
        var remoteRenderer: SurfaceViewRenderer? = null

        if (remoteView != null && localView != null) {
            localRenderer = MediaManager.makeRenderer("Local", localView, eglBase, true)
            remoteRenderer = MediaManager.makeRenderer("Remote", remoteView, eglBase, true)
            localView.addView(localRenderer)
            remoteView.addView(remoteRenderer)
        }

        peerConnectionHandler = PeerConnectionHandler(
            context,
            peerConnectionFactory,
            eglBase,
            makeIceServers(signalingParameter.iceServers),
            peerConnectionParameter,
            this
        )
        peerConnectionHandler.createAnswer()
    }

    fun answer(onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        peerConnectionHandler = PeerConnectionHandler(
            context,
            peerConnectionFactory,
            eglBase,
            makeIceServers(signalingParameter.iceServers),
            peerConnectionParameter,
            this
        )
        peerConnectionHandler.createAnswer()
    }

    fun register(dn: String, userData: JSONObject?, onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        var myUserData = checkMediaOption(userData)

        myDN = dn
        webSocketHandler.register(dn, myUserData) // -> onRegistered()
    }

    fun unregister(dn: String, onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        webSocketHandler.unregister(dn) // -> onUnregistered()
    }

    fun refer(ani: String, onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        webSocketHandler.refer(ani)
        complete(RESULT.SUCCESS, "")
    }


    fun cancel(onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        if (webSocketHandler.cancel()) {
            complete(RESULT.SUCCESS, "")
        } else {
            complete(RESULT.FAIL, "")
        }
    }

    fun reject(onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        webSocketHandler.reject()
        complete(RESULT.SUCCESS, "")
    }

    fun bye(onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        webSocketHandler.bye()
    }

    fun hold(hold: Boolean, onCompleteListener: OnCompleteListener) {
        if (!setCompleteListener(onCompleteListener)) {
            onCompleteListener.onComplete(RESULT.NOT_FINISH, "")
            return
        }

        webSocketHandler.recall(hold, hold, "") // -> onAnswered()
    }

    fun transfer(dn: String) {

    }

    fun reconnect() {

    }

    fun setRemoteImage(image: Drawable) {

    }

    fun setRemoteVisibility(visibility: Boolean) {

    }

    fun isHold(): Boolean {
        return isHold
    }

    fun sendDTMF(dtmf: String, duration: Int, gap: Int): Boolean {
        if (peerConnectionHandler != null) {
            return peerConnectionHandler.sendDTMF(dtmf, duration, gap)
        }

        return false
    }

    /********************************************************************************
     *                                  EventListener                                *
     *********************************************************************************/
    override fun onStateChanged(state: PeerConnectionState.STATE, sdp: SessionDescription?) {
        Logger.d(TAG, "PeerConnection changed state: $state")

        when (state) {
            PeerConnectionState.STATE.IDLE -> null
            PeerConnectionState.STATE.INIT -> null
            PeerConnectionState.STATE.LOCAL_READY -> {
                if (webSocketHandler.signalingState == SignalingState.STATE.RINGING) {
                    webSocketHandler.answer("", sdp!!.description)
                } else if (webSocketHandler.signalingState == SignalingState.STATE.REGISTERED || webSocketHandler.signalingState == SignalingState.STATE.INIT) {
                    if (isReconnect.not()) {
                        if (!ani.isNullOrEmpty()) webSocketHandler.invite(myDN, ani, sdp!!.description)
                    } else {
                        webSocketHandler.recall(true, true, sdp!!.description)
                    }
                }
            }
            PeerConnectionState.STATE.COMPLETE -> {
                // PRESET 상태에선 setRemoteSDP만 실행,
                if (webSocketHandler.signalingState == SignalingState.STATE.PRESET) {
                    // do nothing
                } else if (webSocketHandler.signalingState == SignalingState.STATE.DIALING) {
                    webSocketHandler.establish()
                } else if (webSocketHandler.signalingState == SignalingState.STATE.INIT) {
                    if (isReconnect) {
                        if (webSocketHandler.reconnect()) {
                            eventListener.onEstablished()
                            isReconnect = false
                        }
                    }
                }
            }
        }
    }

    override fun onConnected() {
        Logger.d(TAG, "${webSocketHandler.signalingState}")
        if (webSocketHandler.signalingState == SignalingState.STATE.INIT) {
            if (complete(RESULT.SUCCESS, "")) {

            } else { // reconect code
                stopTimer()

                // TODO onError -> onClose -> req-connect(Reconnect 5 times for 10 seconds) -> req-ws-init -> onConnected -> register
/*                var myUserData = checkMediaOption(null)
                webSocketHandler.register(myDN, myUserData)*/

                peerConnectionHandler = PeerConnectionHandler(
                    context,
                    peerConnectionFactory,
                    eglBase,
                    makeIceServers(signalingParameter.iceServers),
                    peerConnectionParameter,
                    this
                )
                peerConnectionHandler.createOffer()
            }
        }
    }

    override fun onPong() {
        Logger.d(TAG, "onPong ")
        eventListener.onPong()
    }

    override fun onRegistered() {
        if (webSocketHandler.signalingState == SignalingState.STATE.REGISTERED) {
            if (complete(RESULT.SUCCESS, "")) {
            } else {
                myDN = ""
                eventListener.onRegistered()
            }
        }
    }

    override fun onUnregistered() {
        if (webSocketHandler.signalingState == SignalingState.STATE.INIT) {
            if (complete(RESULT.SUCCESS, "")) {
            } else {
                eventListener.onUnregistered()
            }
        }
    }

    override fun onInvited(msg: String) {
        var json = JSONObject(msg)

        if (json.has("from")) {
            ani = json.getString("from")
        }

        eventListener.onInvited(msg)
    }

    override fun onTrying(msg: String) {
        if (webSocketHandler.signalingState == SignalingState.STATE.DIALING) {
            if (complete(RESULT.SUCCESS, msg)) {
            } else {
            }
        }
    }

    override fun onPreset(sdp: String) {
        peerConnectionHandler.setRemoteDescription(sdp)
        eventListener.onPreset(sdp)
    }

    override fun onAnswered(ani: String, msg: String) {
        var json = JSONObject(msg)

        // SDP가 있는 Answer = call 관련된 answer
        if (json.has("sdp")) {
            var sdp = json.getString("sdp")

            if (webSocketHandler.signalingState != SignalingState.STATE.PRESET) {
                peerConnectionHandler.setRemoteDescription(sdp)
            } else {
                eventListener.onAnswered(ani, msg)
                // 상대의 Answer 이후 trying 발신 성공시 Established로 간주함.
                if (webSocketHandler.establish()) {
                    eventListener.onEstablished(msg)
                }
            }
        } else { // SDP가 없는 Answer = Hold/Unhold 및 bitrate 조정
            // Recall에 대한 answer
            // 1차 개발에선 Hold / Unhold만 대응한다.
            if(json.has("mattr")) {
                var mattr = json.getJSONObject("mattr")
                var audioResult = ""
                var videoResult = ""

                // Audio hold값 추출
                if (mattr.has("audio")) {
                    var audioAttr = mattr.getJSONObject("audio")

                    audioResult = audioAttr.getString("mode")
                }

                // Video hold값 추출
                if (mattr.has("video")) {
                    var audioAttr = mattr.getJSONObject("video")

                    videoResult = audioAttr.getString("mode")
                }

                // Audio/Video 중 하나라도 hold 상태라면 hold로 처리
                isHold = audioResult.equals("inactive") || videoResult.equals("sendonly")

                if (webSocketHandler.reconnect()) {
                    complete(RESULT.SUCCESS, msg)
                } else {
                    complete(RESULT.FAIL, msg)
                }
            }
        }
    }

    override fun onEstablished() {
        if (webSocketHandler.signalingState == SignalingState.STATE.ESTABLISHED) {
            complete(RESULT.SUCCESS, "")
            isHold = false
        }
        eventListener.onEstablished()
    }

    override fun onEstablished(sdp: String) {
        if (webSocketHandler.signalingState == SignalingState.STATE.ESTABLISHED) {
            complete(RESULT.SUCCESS, sdp)
            peerConnectionHandler.setRemoteDescription(sdp)
        }
        eventListener.onEstablished(sdp)
    }

    override fun onBye() {
        webSocketHandler.byeAck()
        eventListener.onBye()
    }

    override fun onByeAck() {
        if (webSocketHandler.signalingState == SignalingState.STATE.REGISTERED || webSocketHandler.signalingState == SignalingState.STATE.INIT) {
            complete(RESULT.SUCCESS, "")
        }
    }

    override fun onReject(code: Int, text: String) {
        Logger.e(TAG, "onReject: $code, $text, ${webSocketHandler.signalingState}")
        if (webSocketHandler.signalingState == SignalingState.STATE.INIT) {
            if (complete(RESULT.FAIL, "")) {
            } else {
                eventListener.onReject(code, text)
            }
        } else if (webSocketHandler.signalingState == SignalingState.STATE.REGISTERED) {    // Register 이후 발신 실패시
            if (complete(RESULT.FAIL, "")) {
            } else {
                eventListener.onReject(code, text)
            }
        } else if (webSocketHandler.signalingState == SignalingState.STATE.PRESET) {
            if (complete(RESULT.FAIL, "")) {
            } else {
                eventListener.onReject(code, text)
            }
        } else if (webSocketHandler.signalingState == SignalingState.STATE.DIALING) {
            if (complete(RESULT.FAIL, "")) {
            } else {
                eventListener.onReject(code, text)
            }
        }
    }

    override fun onUpdated() {
        eventListener.onUpdated()
    }

    override fun onCancel() {
        ani = ""

        eventListener.onCancel()
    }

    override fun onRecall() {
        eventListener.onRecall()
    }

    override fun onReconnected() {
        eventListener.onReconnected()
    }

    override fun onNotify() {
        eventListener.onNotify()
    }

    override fun onMessage() {
        eventListener.onMessage()
    }

    override fun onDisconnected() {
        if (webSocketHandler.signalingState == SignalingState.STATE.IDLE) {
            clear()
            complete(RESULT.FAIL, "")
            eventListener.onDisconnected()
        } else if (webSocketHandler.signalingState == SignalingState.STATE.WIFILTESWAP) { // LTE/Wifi swap
            if (isReconnect.not()) {
                startTimer()
                clear()
            }
            if (timerCount < Defines.Timeout.t9Timeout) { // Reconnect 5 times for 10 seconds(t9Timeout)
                wifiLteSwapHandler.postDelayed(wifiLteSwapRunnable, 2000)
            } else {
                stopTimer()
                isReconnect = false
                webSocketHandler.signalingState = SignalingState.STATE.IDLE
                complete(RESULT.FAIL, "")
                eventListener.onDisconnected()
            }
        }
    }

    override fun onError() { // websoket error
        Logger.d(TAG, "onError ")
        eventListener.onError()
    }
}
