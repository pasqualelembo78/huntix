package com.intelligame.huntix.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.intelligame.huntix.bridge.BridgeActivity

object Bridge {

    @JvmStatic
    fun openUnityActivity(context: Context, mode: String) {
        val intent = Intent(context, BridgeActivity::class.java)
        intent.putExtra(BridgeActivity.EXTRA_MODE, mode)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JvmStatic
    fun showToast(message: String) {
        // Called from Unity via AndroidJavaClass
    }

    @JvmStatic
    fun saveData(json: String) {
        // Called from Unity to save data
    }

    @JvmStatic
    fun loadData(): String {
        // Called from Unity to load data
        return "{}"
    }

    @JvmStatic
    fun onUnityMessage(eventName: String, jsonData: String) {
        // Called from Unity to send events to Android
    }
}