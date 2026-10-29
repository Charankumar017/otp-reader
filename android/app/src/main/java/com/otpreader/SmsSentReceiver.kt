package com.otpreader

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

/** Receives the carrier's answer for each forwarded SMS and records it for the app. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        val result = when (resultCode) {
            Activity.RESULT_OK -> "Sent"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                "Failed: carrier rejected it (code 1)" + errorDetail(intent)
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Failed: mobile network is off (airplane mode?)"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "Failed: no network signal"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Failed: message couldn't be encoded"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Failed: too many SMS sent too quickly"
            SmsManager.RESULT_MODEM_ERROR -> "Failed: network refused the message (code 16)"
            else -> "Failed: code $resultCode" + errorDetail(intent)
        }
        OtpForwarder.saveResult(context, number, result)
    }

    // Some phones include the carrier's own error number
    private fun errorDetail(intent: Intent): String {
        val code = intent.getIntExtra("errorCode", -1)
        return if (code != -1) ", network error $code" else ""
    }
}
