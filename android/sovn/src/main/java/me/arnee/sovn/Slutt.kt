package me.arnee.sovn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class Slutt : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timer.fullfor(context)
    }
}
