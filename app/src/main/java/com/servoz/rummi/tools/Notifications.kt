package com.servoz.rummi.tools

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.servoz.rummi.GameActivity
import com.servoz.rummi.MainActivity
import com.servoz.rummi.R
import java.util.*

class Notifications{

    fun create(context: Context, title:String, content:String, gameId:String="", channel:String="MAIN"): Int {
        val id=Integer.parseInt(gameId)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_baseline_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val intent = if(gameId!="")
            Intent(context, GameActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        else
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        intent.putExtra("gameId",gameId)
        builder.setContentIntent(PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT))
        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
        return id
    }
}