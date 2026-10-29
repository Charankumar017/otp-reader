package com.otpreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager

/**
 * Registered in the manifest, so Android starts it for every incoming SMS even when the
 * app is closed. Forwards Hotstar messages only.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!OtpForwarder.canSend(context)) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts[0].originatingAddress ?: ""
        val body = parts.joinToString("") { it.messageBody ?: "" } // long SMS arrive in parts
        if (!OtpForwarder.isHotstar(sender, body)) return

        // Reply from the SIM the Hotstar SMS came in on, not whatever the default SMS SIM is
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        )
        OtpForwarder.setLastTrigger(context)
        OtpForwarder.setLastSubId(context, subId)
        OtpForwarder.send(context, body, subId)
    }
}
