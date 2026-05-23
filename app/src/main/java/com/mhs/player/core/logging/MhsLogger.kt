package com.mhs.player.core.logging

import android.util.Log

object MhsLogger {
    private const val DEFAULT_TAG = "MHSPlayer"

    fun d(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
    }

    fun v(tag: String = DEFAULT_TAG, message: String) {
        Log.v(tag, message)
    }
}
