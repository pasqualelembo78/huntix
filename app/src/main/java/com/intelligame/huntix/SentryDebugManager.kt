package com.intelligame.huntix

import io.sentry.Sentry
import io.sentry.Breadcrumb
import io.sentry.protocol.User

object SentryDebugManager {

    object Category {
        const val GAME = "game"
        const val UI = "ui"
        const val NETWORK = "network"
        const val AR = "ar"
        const val BATTLE = "battle"
        const val PURCHASE = "purchase"
    }

    private var gameMode: String = ""
    private var roomCode: String = ""
    private var userId: String = ""

    fun setGameContext(mode: String, roomCode: String) {
        this.gameMode = mode
        this.roomCode = roomCode
        Sentry.configureScope { scope ->
            scope.setExtra("game_mode", mode)
            scope.setExtra("room_code", roomCode)
            if (userId.isNotBlank()) scope.setUser(User().apply { id = userId })
        }
        breadcrumb(Category.GAME, "Game context set", mapOf("mode" to mode, "roomCode" to roomCode))
    }

    fun setUserContext(userId: String, username: String? = null) {
        this.userId = userId
        Sentry.configureScope { scope ->
            val user = User().apply {
                id = userId
                username = username
            }
            scope.setUser(user)
        }
    }

    fun breadcrumb(category: String, msg: String, data: Map<String, Any?> = emptyMap()) {
        val b = Breadcrumb().apply {
            this.category = category
            this.message = msg
            this.type = io.sentry.BreadcrumbType.INFO
            data.forEach { (k, v) -> setData(k, v?.toString() ?: "null") }
        }
        Sentry.addBreadcrumb(b)
    }

    fun clearGameContext() {
        Sentry.configureScope { scope ->
            scope.setExtra("game_mode", "")
            scope.setExtra("room_code", "")
        }
        breadcrumb(Category.GAME, "Context cleared")
        gameMode = ""
        roomCode = ""
    }

    fun captureException(e: Throwable) {
        Sentry.captureException(e)
    }

    fun captureMessage(message: String, level: io.sentry.SentryLevel = io.sentry.SentryLevel.INFO) {
        Sentry.captureMessage(message, level)
    }
}