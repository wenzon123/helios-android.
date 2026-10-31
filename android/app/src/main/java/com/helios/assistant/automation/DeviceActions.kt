package com.helios.assistant.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings

class DeviceActions(private val context: Context) {
    fun openYouTube() {
        val launch = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        context.startActivity((launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun searchWeb(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun openSettings() = context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun setAlarm(hour: Int, minute: Int, label: String = "HÉLIOS") {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}