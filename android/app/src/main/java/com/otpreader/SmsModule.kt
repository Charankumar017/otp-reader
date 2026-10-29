package com.otpreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap

class SmsModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {

    private var receiver: BroadcastReceiver? = null

    override fun getName() = "SmsModule"

    private fun toMap(sender: String, body: String, date: Long): WritableMap =
        Arguments.createMap().apply {
            putString("sender", sender)
            putString("body", body)
            putDouble("date", date.toDouble())
        }

    /** Emits an "onSms" event to JS for every incoming SMS while the app process is alive. */
    @ReactMethod
    fun startListening() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                if (parts.isEmpty()) return
                val sender = parts[0].originatingAddress ?: ""
                val body = parts.joinToString("") { it.messageBody ?: "" } // long SMS arrive in parts
                ctx.emitDeviceEvent("onSms", toMap(sender, body, parts[0].timestampMillis))
            }
        }
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            "android.permission.BROADCAST_SMS", // only the system can send to us
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    @ReactMethod
    fun stopListening() {
        receiver?.let { ctx.unregisterReceiver(it) }
        receiver = null
    }

    /** Resolves the newest inbox SMS whose sender or body contains `keyword`, or null. */
    @ReactMethod
    fun getLatest(keyword: String, promise: Promise) {
        try {
            val cursor = ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null,
                "${Telephony.Sms.DATE} DESC LIMIT 100"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val sender = it.getString(0) ?: ""
                    val body = it.getString(1) ?: ""
                    if (sender.contains(keyword, true) || body.contains(keyword, true)) {
                        promise.resolve(toMap(sender, body, it.getLong(2)))
                        return
                    }
                }
            }
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SMS_READ_ERROR", e)
        }
    }

    /** Family numbers that Hotstar SMS are forwarded to. */
    override fun getConstants(): Map<String, Any> =
        mapOf("recipients" to OtpForwarder.RECIPIENTS)

    /** Forwards `body` to family by hand; resolves how many numbers it was sent to. */
    @ReactMethod
    fun forward(body: String, promise: Promise) {
        try {
            promise.resolve(OtpForwarder.send(ctx, body, OtpForwarder.lastSubId(ctx)))
        } catch (e: Exception) {
            promise.reject("SMS_SEND_ERROR", e)
        }
    }

    /** Resolves a JSON map of number -> { result, time } for the latest forward. */
    @ReactMethod
    fun getResults(promise: Promise) {
        promise.resolve(OtpForwarder.getResults(ctx))
    }

    /** Resolves when the automatic forward last ran (ms since epoch), or 0 if never. */
    @ReactMethod
    fun getLastTrigger(promise: Promise) {
        promise.resolve(OtpForwarder.lastTrigger(ctx).toDouble())
    }

    // Required by NativeEventEmitter
    @ReactMethod fun addListener(eventName: String) {}
    @ReactMethod fun removeListeners(count: Int) {}

    override fun invalidate() {
        stopListening()
        super.invalidate()
    }
}
