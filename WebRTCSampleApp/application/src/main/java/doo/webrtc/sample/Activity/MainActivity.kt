package doo.webrtc.sample.Activity

import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import doo.webrtc.library.WebRTCLibrary
import doo.webrtc.library.PeerConnectionParameter
import doo.webrtc.library.SignalingParameter
import java.util.ArrayList
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.CompoundButton
import doo.webrtc.library.ServerInfo
import doo.webrtc.library.interfaces.SignalingEventListener
import doo.webrtc.sample.Adapter.CustomAdapter
import doo.webrtc.sample.R

class MainActivity : AppCompatActivity(),
    SignalingEventListener {
    private val TAG = "MainActivity"
    private val REQUEST_PERMISSION_CHECK: Int = 0
    private val REQUEST_CODE_MAKE_CALL: Int = 1
    private val REQUEST_CODE_NEW_CALL: Int = 2

    private var gcl: WebRTCLibrary? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView_LOG.text = ""

        button_CONNECT.isEnabled = true
        button_DISCONNECT.isEnabled = false
        button_REGISTER.isEnabled = false
        button_UNREGISTER.isEnabled = false
        button_CALL.isEnabled = false
        button_ANSWER.isEnabled = false
        button_ENDCALL.isEnabled = false
        button_REJECT.isEnabled = false
        button_CANCEL.isEnabled = false
        button_REFER.isEnabled = false

        setComponentEventListener()
    }

    override fun onStart() {
        super.onStart()
    }

    fun checkAndRequestPermission(permissionRequestCode: Int, vararg permissions: String): Boolean {
        val requiredPermissions = getRequiredPermissions(*permissions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requiredPermissions.size > 0 && !isDestroyed) {
                requestPermissions(permissions, permissionRequestCode)
                return false
            } else {
                return true
            }
        } else {
            return true
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun getRequiredPermissions(vararg permissions: String): Array<String> {
        val requiredPermissions = ArrayList<String>()

        for (permission in permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(permission)
            }
        }

        return requiredPermissions.toTypedArray()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        for (i in permissions.indices) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, permissions[i])
                showPermissionAlertDialog()
            }
        }
    }

    private fun showPermissionAlertDialog() {
        val alt_bld = if (android.os.Build.VERSION.SDK_INT < 11)
            AlertDialog.Builder(this)
        else
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
        alt_bld.setPositiveButton("종료하기") { dialog, whichButton ->
            dialog.dismiss()
            finish()
        }

        val alert = alt_bld.create()
        alert.setTitle("알림")
        alert.setMessage("정상적인 서비스 이용을 위해서는 권한 설정에 모두 동의해주셔야 합니다")
        alert.show()
    }

    private fun setComponentEventListener() {
        button_CONNECT.setOnClickListener {
            sendLog("[INPUT]\t\tCONNECT")
            button_CONNECT.isEnabled = false

            if (gcl == null) {
                gcl = createCallLibrary()
            }

            gcl!!.connect(
                editText_IP.text.toString(),
                editText_PORT.text.toString(),
                object : WebRTCLibrary.OnCompleteListener {
                    override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                        Handler(Looper.getMainLooper()).post {
                            sendLog("[CALLBACK] CONNECTED: $result")

                            if (result == WebRTCLibrary.RESULT.SUCCESS) {
                                button_DISCONNECT.isEnabled = true
                                button_REGISTER.isEnabled = true
                                button_CALL.isEnabled = true
                            } else {
                                button_CONNECT.isEnabled = true
                                button_DISCONNECT.isEnabled = false
                                button_REGISTER.isEnabled = false
                                button_CALL.isEnabled = false
                            }
                        }
                    }
                })
        }

        button_DISCONNECT.setOnClickListener {
            sendLog("[INPUT]\t\tDISCONNECT")

            gcl!!.disconnect()
        }

        button_REGISTER.setOnClickListener {
            sendLog("[INPUT]\t\tREGISTER")
            button_REGISTER.isEnabled = false

            gcl!!.register(editText_DN.text.toString(), null, object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] REGISTERED: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            button_REGISTER.isEnabled = false
                            button_UNREGISTER.isEnabled = true
                            button_UNREGISTER.isEnabled = true
                            button_CALL.isEnabled = true
                        } else {
                            button_REGISTER.isEnabled = true
                        }
                    }
                }
            })
        }

        button_UNREGISTER.setOnClickListener {
            sendLog("[INPUT]\t\tUNREGISTER")
            button_UNREGISTER.isEnabled = false

            gcl!!.unregister(editText_DN.text.toString(), object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] UNREGISTERED: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            button_REGISTER.isEnabled = true
                            button_UNREGISTER.isEnabled = false
                        } else {
                            button_REGISTER.isEnabled = false
                            button_UNREGISTER.isEnabled = true
                        }
                    }
                }
            })
        }

        button_CALL.setOnClickListener {
            sendLog("[INPUT]\t\tINVITE")

            button_CALL.isEnabled = false
            gcl!!.invite(
                editText_DN.text.toString(),
                editText_ANI.text.toString(),
                localVideoArea,
                remoteVideoArea,
                object : WebRTCLibrary.OnCompleteListener {
                    override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                        Handler(Looper.getMainLooper()).post {
                            sendLog("[CALLBACK] INVITE: $result")

                            if (result == WebRTCLibrary.RESULT.SUCCESS) {
                                button_ENDCALL.isEnabled = false
                                button_CANCEL.isEnabled = true
                            } else {
                                button_CALL.isEnabled = true
                                button_ENDCALL.isEnabled = false
                                button_CANCEL.isEnabled = false
                            }
                        }
                    }
                })
        }

        button_ANSWER.setOnClickListener {
            sendLog("[INPUT]\t\tANSWER")
            button_ANSWER.isEnabled = false
            button_REJECT.isEnabled = false

            gcl!!.answer(localVideoArea, remoteVideoArea, object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] ANSWER: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            button_CALL.isEnabled = false
                            button_ANSWER.isEnabled = false
                            button_ENDCALL.isEnabled = true
                            button_CANCEL.isEnabled = false
                            button_REJECT.isEnabled = false
                        } else {
                            button_ANSWER.isEnabled = true
                            button_REJECT.isEnabled = true
                        }
                    }
                }
            })
        }

        button_ENDCALL.setOnClickListener {
            sendLog("[INPUT]\t\tBYE")

            button_ENDCALL.isEnabled = false
            gcl!!.bye(object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] BYE: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            showLogView()
                            enableCallButton()
                        } else {
                            button_ENDCALL.isEnabled = true
                        }
                    }
                }
            })
        }

        button_REFER.setOnClickListener {
            var ani = editText_ANI.text.toString()
            sendLog("[INPUT]\t\tREFER CALL: $ani")

            button_REFER.isEnabled = false
            gcl!!.refer(ani, object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] REFER: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            enableCallButton()
                        } else {
                            button_REFER.isEnabled = true
                        }
                    }
                }
            })
        }

        button_CANCEL.setOnClickListener {
            sendLog("[INPUT]\t\tCANCEL CALL")

            button_CANCEL.isEnabled = false
            gcl!!.cancel(object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] CANCEL: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            showLogView()

                            button_CALL.isEnabled = true
                        } else {
                            button_CANCEL.isEnabled = true
                        }
                    }
                }
            })
        }

        button_REJECT.setOnClickListener {
            sendLog("[INPUT]\t\tREJECT CALL")
            gcl!!.reject(object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] REJECT: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                            enableCallButton()
                        } else {
                        }
                    }
                }
            })
        }

        radio_HOLD.setOnClickListener {
            sendLog("[INPUT]\t\tHOLD")
            gcl!!.hold(true, object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] HOLD: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                        } else {
                        }
                    }
                }
            })
        }

        radio_UNHOLD.setOnClickListener {
            sendLog("[INPUT]\t\tUNHOLD")
            gcl!!.hold(false, object : WebRTCLibrary.OnCompleteListener {
                override fun onComplete(result: WebRTCLibrary.RESULT, msg: String) {
                    Handler(Looper.getMainLooper()).post {
                        sendLog("[CALLBACK] UNHOLD: $result")

                        if (result == WebRTCLibrary.RESULT.SUCCESS) {
                        } else {
                        }
                    }
                }
            })
        }

        checkbox_VIDEO.setOnCheckedChangeListener() { compoundButton: CompoundButton, b: Boolean ->
        }

        checkbox_AUDIO.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
        }



        button_log.setOnClickListener {
            showLogView()
        }

        button_server.setOnClickListener {
            showServerView()
        }

        button_camera.setOnClickListener {
            showCameraView()
        }
    }

    private fun showLogView() {
        recyclerView_ice.visibility = View.INVISIBLE
        textView_LOG.visibility = View.VISIBLE
        localVideoArea.visibility = View.INVISIBLE

        button_addTurn.visibility = View.INVISIBLE
        button_deleteTurn.visibility = View.INVISIBLE
        button_modifyTurn.visibility = View.INVISIBLE
    }

    private fun showServerView() {
        recyclerView_ice.visibility = View.VISIBLE
        textView_LOG.visibility = View.INVISIBLE
        localVideoArea.visibility = View.INVISIBLE

        button_addTurn.visibility = View.VISIBLE
        button_deleteTurn.visibility = View.VISIBLE
        button_modifyTurn.visibility = View.VISIBLE
    }

    private fun showCameraView() {
        recyclerView_ice.visibility = View.INVISIBLE
        recyclerView_ice.visibility = View.INVISIBLE
        localVideoArea.visibility = View.VISIBLE

        button_addTurn.visibility = View.INVISIBLE
        button_deleteTurn.visibility = View.INVISIBLE
        button_modifyTurn.visibility = View.INVISIBLE
    }

    private fun enableCallButton() {
        button_CALL.isEnabled = true
        button_ANSWER.isEnabled = false
        button_ENDCALL.isEnabled = false
        button_CANCEL.isEnabled = false
        button_REJECT.isEnabled = false
        button_REFER.isEnabled = false
    }

    fun sendLog(str: String) {
        var lastStr = textView_LOG.editableText.toString()
        textView_LOG.text = "${lastStr}\n${str}"
    }

    fun createCallLibrary(): WebRTCLibrary {
        var list = listOf(
            ServerInfo(
                "turn:100.100.107.202",
                "3478",
                "webrtc",
                "ipron"
            ).toString(),
            ServerInfo("stun:100.100.107.202", "3478", "", "").toString()
        )

        recyclerView_ice.layoutManager = LinearLayoutManager(this)
        recyclerView_ice.adapter = CustomAdapter(this, list)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermission(
                REQUEST_PERMISSION_CHECK,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS
            )
        }

        /*
        *   Set WebRTCLibrary parameter
        */
        var iceServers = list
        var resolution: WebRTCLibrary.RESOLUTION? = null
        var cameraOption: WebRTCLibrary.CAMERA_OPTION? = null
        var viewMode: WebRTCLibrary.VIEW_MODE? = null

        var mediaOption = WebRTCLibrary.MEDIA_OPTION.AUDIO_VIDEO
        if (checkbox_VIDEO.isChecked && checkbox_AUDIO.isChecked) {
            mediaOption = WebRTCLibrary.MEDIA_OPTION.AUDIO_VIDEO
            resolution = WebRTCLibrary.RESOLUTION.VGA
            cameraOption = WebRTCLibrary.CAMERA_OPTION.FRONT
            viewMode = WebRTCLibrary.VIEW_MODE.SEE_ME
        } else if (checkbox_VIDEO.isChecked) {
            mediaOption = WebRTCLibrary.MEDIA_OPTION.VIDEO
            resolution = WebRTCLibrary.RESOLUTION.VGA
            cameraOption = WebRTCLibrary.CAMERA_OPTION.FRONT
            viewMode = WebRTCLibrary.VIEW_MODE.SEE_ME
        } else if (checkbox_AUDIO.isChecked) {
            mediaOption = WebRTCLibrary.MEDIA_OPTION.AUDIO
        }

        val pcParam = PeerConnectionParameter(
            mediaOption,
            resolution,
            cameraOption,
            viewMode,
            false
        )
        val signalParam = SignalingParameter(iceServers)

        return WebRTCLibrary(this, this, pcParam, signalParam)
    }

    /********************************************************************************
     *                               Event Listener                                 *
     ********************************************************************************/

    override fun onConnected() {
        sendLog("[Event] onConnected")
    }

    override fun onDisconnected() {
        Handler(Looper.getMainLooper()).post {
            button_CONNECT.isEnabled = true
            button_DISCONNECT.isEnabled = false
            button_REGISTER.isEnabled = false
            button_UNREGISTER.isEnabled = false
            button_CALL.isEnabled = false
            button_ANSWER.isEnabled = false
            button_ENDCALL.isEnabled = false
            button_REJECT.isEnabled = false
            button_CANCEL.isEnabled = false

            showLogView()
            sendLog("[Event] onDisconnected")
        }
    }

    override fun onPong() {
        sendLog("[Event] onPong")
    }

    override fun onRegistered() {
        sendLog("[Event] onRegistered")
    }

    override fun onUnregistered() {
        sendLog("[Event] onUnregistered")
    }

    override fun onInvited(msg: String) {
        sendLog("[Event] onInvited: $msg")

        Handler(Looper.getMainLooper()).post {
            button_CALL.isEnabled = false
            button_ANSWER.isEnabled = true
            button_CANCEL.isEnabled = false
            button_REJECT.isEnabled = true
            button_ENDCALL.isEnabled = false
        }
    }

    override fun onTrying(msg: String) {
        sendLog("[Event] onTrying: $msg")
    }

    override fun onPreset(sdp: String) {
        sendLog("[Event] onPreset")
    }

    override fun onAnswered(ani: String, msg: String) {
        sendLog("[Event] onAnswered")

        Handler(Looper.getMainLooper()).post {
            button_CALL.isEnabled = false
            button_ANSWER.isEnabled = false
            button_CANCEL.isEnabled = false
            button_REJECT.isEnabled = false
            button_ENDCALL.isEnabled = true
        }
    }

    override fun onEstablished() {
        sendLog("[Event] onEstablished")

        Handler(Looper.getMainLooper()).post {
            showCameraView()

            button_CALL.isEnabled = false
            button_ANSWER.isEnabled = false
            button_CANCEL.isEnabled = false
            button_REJECT.isEnabled = false
            button_ENDCALL.isEnabled = true
            button_REFER.isEnabled = true
        }
    }

    override fun onEstablished(sdp: String) {
        sendLog("[Event] onEstablished")

        Handler(Looper.getMainLooper()).post {
            showCameraView()

            button_CALL.isEnabled = false
            button_ANSWER.isEnabled = false
            button_CANCEL.isEnabled = false
            button_REJECT.isEnabled = false
            button_ENDCALL.isEnabled = true
            button_REFER.isEnabled = true
        }
    }

    override fun onBye() {
        sendLog("[Event] onBye")

        Handler(Looper.getMainLooper()).post {
            showLogView()

            button_CALL.isEnabled = true
            button_ANSWER.isEnabled = false
            button_CANCEL.isEnabled = false
            button_REJECT.isEnabled = false
            button_ENDCALL.isEnabled = false
            button_REFER.isEnabled = false
        }
    }

    override fun onByeAck() {
        sendLog("[Event] onByeAck")
    }

    override fun onReject(code: Int, text: String) {
        sendLog("[Event] onReject: $code, $text")
    }

    override fun onUpdated() {
        sendLog("[Event] onUpdated")
    }

    override fun onCancel() {
        sendLog("[Event] onCancel")
    }

    override fun onRecall() {
        sendLog("[Event] onRecall")
    }

    override fun onReconnected() {
        sendLog("[Event] onReconnected")
    }

    override fun onNotify() {
        sendLog("[Event] onNotify")
    }

    override fun onMessage() {
        sendLog("[Event] onMessage")
    }

    override fun onError() {
        sendLog("[Event] onError")
    }
}
