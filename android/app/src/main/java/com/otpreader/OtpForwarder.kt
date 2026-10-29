package com.otpreader

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Forwards Hotstar SMS to family members by SMS. */
object OtpForwarder {
    private const val TAG = "OtpForwarder"
    private const val KEYWORD = "hotstar" // matches "Hotstar" and "JioHotstar"
    private const val PREFS = "otp_reader"

    // Family members who get every Hotstar OTP
    val RECIPIENTS = listOf("9441692202", "7032384295")

    // Jio blocks SMS that copy Hotstar's "verification code" wording, so send our own text.
    // {otp} is replaced with the code.
    private const val MESSAGE = "Hotstar login: {otp}"

    // First standalone 4-6 digit number in the message
    private fun extractOtp(body: String) = Regex("\\b\\d{4,6}\\b").find(body)?.value

    /** Short text for family, or null if no code could be found in the SMS. */
    fun messageFor(body: String) = extractOtp(body)?.let { MESSAGE.replace("{otp}", it) }

    fun isHotstar(sender: String, body: String) =
        sender.contains(KEYWORD, true) || body.contains(KEYWORD, true)

    fun canSend(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** SmsManager for the given SIM, or the default SMS SIM if it's unknown. */
    private fun smsManager(c: Context, subId: Int): SmsManager {
        val valid = subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = c.getSystemService(SmsManager::class.java)
            if (valid) base.createForSubscriptionId(subId) else base
        } else {
            @Suppress("DEPRECATION")
            if (valid) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }

    /**
     * Sends the OTP in `body` to every recipient from SIM `subId` (pass INVALID_SUBSCRIPTION_ID for the
     * default). Returns how many sends were handed to the carrier; the carrier's answer
     * arrives later in SmsSentReceiver.
     */
    fun send(c: Context, body: String, subId: Int): Int {
        val text = messageFor(body) ?: run {
            Log.w(TAG, "No OTP found in Hotstar SMS, not forwarding")
            return 0
        }
        val sms = smsManager(c, subId)
        val parts = sms.divideMessage(text) // long messages go out as several SMS
        var sent = 0
        for ((i, number) in RECIPIENTS.withIndex()) {
            saveResult(c, number, "Sending...")
            try {
                val sentIntents = ArrayList(parts.indices.map { p ->
                    PendingIntent.getBroadcast(
                        c,
                        i * 100 + p, // unique per number and part
                        Intent(c, SmsSentReceiver::class.java).putExtra("number", number),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                })
                sms.sendMultipartTextMessage(number, null, parts, sentIntents, null)
                sent++
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't send to $number", e)
                saveResult(c, number, "Failed: ${e.message}")
            }
        }
        return sent
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** SIM the last Hotstar SMS arrived on, so manual resends use it too. */
    fun lastSubId(c: Context) =
        prefs(c).getInt("lastSubId", SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    fun setLastSubId(c: Context, subId: Int) = prefs(c).edit().putInt("lastSubId", subId).apply()

    /** When the automatic forward last ran, to spot the phone delaying the app. */
    fun setLastTrigger(c: Context) =
        prefs(c).edit().putLong("lastTrigger", System.currentTimeMillis()).apply()

    fun lastTrigger(c: Context) = prefs(c).getLong("lastTrigger", 0L)

    /** Remembers the latest send result per number so the app can show it. */
    fun saveResult(c: Context, number: String, result: String) {
        val prefs = prefs(c)
        val all = JSONObject(prefs.getString("results", "{}")!!)
        all.put(number, JSONObject().put("result", result).put("time", System.currentTimeMillis()))
        prefs.edit().putString("results", all.toString()).apply()
    }

    fun getResults(c: Context): String = prefs(c).getString("results", "{}")!!
}
