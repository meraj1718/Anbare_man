package com.example.anbarman

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: "کالا"
        val date = intent.getStringExtra("date") ?: ""
        val days = intent.getIntExtra("days", 7)
        val label = intent.getStringExtra("label") ?: "تاریخ انقضا"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("expiry", "هشدار تاریخ‌ها", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val open = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            (name + date + label).hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (days <= 0) {
            "$name: $label رسیده است"
        } else {
            "$name: $label حدود $days روز دیگر است"
        }

        manager.notify(
            (name + date + label).hashCode(),
            NotificationCompat.Builder(context, "expiry")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("هشدار انبار من")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }
}
