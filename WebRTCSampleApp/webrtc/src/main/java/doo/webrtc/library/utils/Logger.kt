package doo.webrtc.library.utils

import android.util.Log
import doo.webrtc.library.BuildConfig

object Logger {
    private val TAG = "Logger"

    init {
    }

    fun d(header: String, body: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[$header] $body")
        }
    }

    fun s(header: String, body: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[$header][STEP] $body")
        }
    }

    fun w(header: String, body: String) {
        Log.w(TAG, "[$header] $body")
    }

    fun e(header: String, body: String) {
        Log.e(TAG, "[$header] $body")
    }

    fun r(header: String, body: String) {
        Log.d(TAG, "[$header] $body")
    }
}