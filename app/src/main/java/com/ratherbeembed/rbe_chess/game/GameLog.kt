package com.ratherbeembed.rbe_chess.game

/**
 * Logging seam so the game logic can run in plain JVM tests, where
 * android.util.Log is not available.
 */
interface GameLog {
    fun d(message: String)
    fun e(message: String, error: Throwable)

    companion object {
        val NONE: GameLog = object : GameLog {
            override fun d(message: String) = Unit
            override fun e(message: String, error: Throwable) = Unit
        }
    }
}
