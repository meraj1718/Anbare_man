package com.example.anbarman

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale

class BootReceiver : BroadcastReceiver() {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED && intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences("store", Context.MODE_PRIVATE)
        val items = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until items.length()) {
            val product = items.optJSONObject(i) ?: continue
            val batches = product.optJSONArray("batches") ?: continue
            for (j in 0 until batches.length()) {
                val batch = batches.optJSONObject(j) ?: continue
                val date = try { format.parse(batch.optString("expiry"))?.time } catch (_: Exception) { null } ?: continue
                val days = batch.optInt("alarm", 7).coerceAtLeast(0)
                val trigger = date - days * 86_400_000L
                if (trigger <= System.currentTimeMillis()) continue
                val requestCode = (product.optString("barcode") + "|" + product.optString("name") + "|" + batch.optString("id", batch.optString("expiry"))).hashCode()
                val alarmIntent = Intent(context, ExpiryReceiver::class.java).apply {
                    putExtra("name", product.optString("name", "کالا"))
                    putExtra("date", batch.optString("expiry"))
                    putExtra("days", days)
                }
                val pending = PendingIntent.getBroadcast(
                    context, requestCode, alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarms.set(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }
}
