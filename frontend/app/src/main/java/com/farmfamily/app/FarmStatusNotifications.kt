package com.farmfamily.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

internal object FarmStatusNotifications {
    private const val CHANNEL_ID = "family_activity"

    fun process(context: Context, familyId: String, rows: List<JSONObject>) {
        val preferences = context.getSharedPreferences("farm_family_preferences", Context.MODE_PRIVATE)
        val key = "known_status_notifications_$familyId"
        val known = preferences.getStringSet(key, null)
        val currentIds = rows.map { it.optString("id") }.filter(String::isNotBlank).toSet()
        if (known == null) {
            preferences.edit().putStringSet(key, currentIds).apply()
            return
        }
        val newlyCreated = rows.filter { row ->
            row.optString("id") !in known && row.isNull("read_at")
        }
        if (newlyCreated.isEmpty()) {
            preferences.edit().putStringSet(key, (known + currentIds).sorted().takeLast(500).toSet()).apply()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Family activity", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        newlyCreated.take(3).forEach { row ->
            val title = row.optString("content_key").replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Family activity" }
            val text = row.optJSONObject("content_args")?.let(::notificationSummary).orEmpty().ifBlank { "There is new activity in your family farm." }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(row.optString("id").hashCode(), notification) }
        }
        preferences.edit().putStringSet(key, (known + currentIds).sorted().takeLast(500).toSet()).apply()
    }

    private fun notificationSummary(args: JSONObject): String = args.keys().asSequence()
        .mapNotNull { key -> args.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank) }
        .take(2).joinToString(" · ")
}
