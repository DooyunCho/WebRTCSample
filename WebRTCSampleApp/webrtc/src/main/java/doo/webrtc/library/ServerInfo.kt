package doo.webrtc.library

class ServerInfo(val ip: String, val port: String, val userName: String, val credential: String) {

    override fun toString(): String {
        var result = ""

        if (userName.isEmpty() && credential.isEmpty()) {
            // stun
            result = "{\"url\":\"$ip:$port\"}"
        } else {
            // turn
            result = "{\"url\":\"$ip:$port\",\"credential\":\"$credential\",\"username\":\"$userName\"}"
        }

        return result
    }
}