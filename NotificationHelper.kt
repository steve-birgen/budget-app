package com.yourapp.budgetapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_REMINDERS = "bill_reminders"
        const val CHANNEL_ID_SUMMARIES = "spending_summaries"
        const val CHANNEL_ID_ALERTS = "budget_alerts"
        const val CHANNEL_ID_GOALS = "goal_reminders"
        const val CHANNEL_ID_SUBSCRIPTIONS = "subscription_reminders"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val remindersChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Bill Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for upcoming bills"
            }

            val summariesChannel = NotificationChannel(
                CHANNEL_ID_SUMMARIES,
                "Spending Summaries",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily, weekly and monthly spending summaries"
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when budget limits are reached"
            }

            val goalsChannel = NotificationChannel(
                CHANNEL_ID_GOALS,
                "Goal Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders for savings and financial goals"
            }

            val subscriptionsChannel = NotificationChannel(
                CHANNEL_ID_SUBSCRIPTIONS,
                "Subscription Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for recurring subscriptions"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(remindersChannel)
            manager.createNotificationChannel(summariesChannel)
            manager.createNotificationChannel(alertsChannel)
            manager.createNotificationChannel(goalsChannel)
            manager.createNotificationChannel(subscriptionsChannel)
        }
    }

    fun showNotification(channelId: String, title: String, message: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // Handle permission not granted
            }
        }
    }
}
