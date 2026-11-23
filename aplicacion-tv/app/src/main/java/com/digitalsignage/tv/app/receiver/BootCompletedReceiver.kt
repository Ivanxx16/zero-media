package com.digitalsignage.tv.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.digitalsignage.tv.MainActivity
import com.digitalsignage.tv.core.common.logging.SignageLogger

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        SignageLogger.i(TAG, "Device boot event received: action=$action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try {
                context.startActivity(launchIntent)
                SignageLogger.i(TAG, "Successfully launched MainActivity on TV boot")
            } catch (e: Exception) {
                SignageLogger.e(TAG, "Failed to launch MainActivity on boot", e)
            }
        }
    }
}
