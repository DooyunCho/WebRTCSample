package doo.webrtc.library.webrtc

import android.util.Log
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import doo.webrtc.library.utils.Logger
import org.webrtc.PeerConnection
import java.util.concurrent.Executor
import java.util.regex.Pattern

class SDPObserver(val executor: Executor, val peerConnection: PeerConnection): SdpObserver {
    private val TAG = "SDPObserver"

    override fun onSetFailure(p0: String?) {
        Logger.e(TAG, "onSetFailure: $p0")
    }

    override fun onSetSuccess() {
        Logger.d(TAG, "onSetSuccess")
    }

    override fun onCreateSuccess(origSdp: SessionDescription?) {
        val tempSdpStr = origSdp?.description

        val sdp = SessionDescription(origSdp?.type, preferISAC(tempSdpStr!!))

        Logger.d(TAG, "LocalSDPObserver.onCreateSuccess() changeSdp: " + sdp.description)

        executor.execute{ peerConnection.setLocalDescription(this, sdp)}
    }

    override fun onCreateFailure(p0: String?) {
        Logger.e(TAG, "onCreateFailure: $p0")
    }

    // Mangle SDP to prefer ISAC/16000 over any other audio codec.
    private fun preferISAC(sdpDescription: String): String {
        val lines = sdpDescription.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var mLineIndex = -1
        var isac16kRtpMap: String? = null
        val isac16kPattern = Pattern.compile("^a=rtpmap:(\\d+) ISAC/16000[\r]?$")
        var i = 0
        while (i < lines.size && (mLineIndex == -1 || isac16kRtpMap == null)) {
            if (lines[i].startsWith("m=audio ")) {
                mLineIndex = i
                ++i
                continue
            }
            val isac16kMatcher = isac16kPattern.matcher(lines[i])
            if (isac16kMatcher.matches()) {
                isac16kRtpMap = isac16kMatcher.group(1)
                ++i
                continue
            }
            ++i
        }
        if (mLineIndex == -1) {
            Logger.d(TAG, "No m=audio line, so can't prefer iSAC")
            return sdpDescription
        }
        if (isac16kRtpMap == null) {
            Logger.d(TAG, "No ISAC/16000 line, so can't prefer iSAC")
            return sdpDescription
        }
        val origMLineParts = lines[mLineIndex].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val newMLine = StringBuilder()
        var origPartIndex = 0
        // Format is: m=<media> <port> <proto> <fmt> ...
        newMLine.append(origMLineParts[origPartIndex++]).append(" ")
        newMLine.append(origMLineParts[origPartIndex++]).append(" ")
        newMLine.append(origMLineParts[origPartIndex++]).append(" ")
        newMLine.append(isac16kRtpMap)
        while (origPartIndex < origMLineParts.size) {
            if (origMLineParts[origPartIndex] != isac16kRtpMap) {
                newMLine.append(" ").append(origMLineParts[origPartIndex])
            }
            ++origPartIndex
        }
        lines[mLineIndex] = newMLine.toString()
        val newSdpDescription = StringBuilder()
        for (line in lines) {
            newSdpDescription.append(line).append("\r\n")
        }
        return newSdpDescription.toString()
    }
}