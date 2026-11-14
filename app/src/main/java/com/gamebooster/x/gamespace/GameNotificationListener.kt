package com.gamebooster.x.gamespace

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class IncomingNotification(val appLabel: String, val title: String, val text: String)

/**
 * Recebe notificações (com permissão do usuário) para mostrá-las como texto discreto
 * no painel do jogo. Só repassa enquanto há uma sessão de jogo ativa; nada é salvo.
 */
class GameNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!sessionActive || sbn.packageName == packageName || sbn.isOngoing) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        events.tryEmit(IncomingNotification(label, title, text))
    }

    companion object {
        @Volatile
        var sessionActive: Boolean = false

        private val events = MutableSharedFlow<IncomingNotification>(
            extraBufferCapacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        val notifications: SharedFlow<IncomingNotification> = events.asSharedFlow()
    }
}
