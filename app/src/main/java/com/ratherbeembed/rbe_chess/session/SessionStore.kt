package com.ratherbeembed.rbe_chess.session

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("rbe_chess_session", Context.MODE_PRIVATE)

    fun load(): SessionSnapshot? =
        prefs.getString(KEY_SNAPSHOT, null)
            ?.let(SessionSnapshotCodec::decode)

    fun save(snapshot: SessionSnapshot) {
        prefs.edit()
            .putString(KEY_SNAPSHOT, SessionSnapshotCodec.encode(snapshot))
            .apply()
    }

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
