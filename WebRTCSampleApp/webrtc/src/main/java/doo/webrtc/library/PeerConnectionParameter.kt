package doo.webrtc.library

class PeerConnectionParameter(
    val mediaOption: WebRTCLibrary.MEDIA_OPTION,
    val resolution: WebRTCLibrary.RESOLUTION?,
    val cameraOption: WebRTCLibrary.CAMERA_OPTION?,
    val viewMode: WebRTCLibrary.VIEW_MODE?,
    val signLanguage:Boolean
) {
}