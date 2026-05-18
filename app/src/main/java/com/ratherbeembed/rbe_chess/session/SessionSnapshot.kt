package com.ratherbeembed.rbe_chess.session

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.GameEndReason
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.TerminalState
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.input.PromotionPickState
import com.ratherbeembed.rbe_chess.input.PromotionPiece
import com.ratherbeembed.rbe_chess.ui.AppPhase
import com.ratherbeembed.rbe_chess.ui.FinishedGameUiState
import com.ratherbeembed.rbe_chess.ui.GameMode
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

data class SessionSnapshot(
    val phase: AppPhase,
    val moveHistory: MoveHistory,
    val moveBuffer: MoveBuffer,
    val gameMode: GameMode,
    val playerSide: ChessSide,
    val terminalState: TerminalState?,
    val finishedGame: FinishedGameUiState?,
    val promotionPick: PromotionPickState?,
    val batteryPct: Int?,
    val miniKeyboardVisible: Boolean,
)

object SessionSnapshotCodec {
    private const val VERSION = "1"
    private const val SEP = ","

    fun encode(snapshot: SessionSnapshot): String {
        val props = Properties().apply {
            setProperty("version", VERSION)
            when (val phase = snapshot.phase) {
                is AppPhase.StartMenu -> {
                    setProperty("phase", "start")
                    setProperty("startIndex", phase.selectedIndex.toString())
                }
                AppPhase.InGame -> setProperty("phase", "inGame")
            }
            setProperty("moves", snapshot.moveHistory.moves.joinToString(SEP))
            snapshot.moveBuffer.writeTo(this)
            setProperty("gameMode", snapshot.gameMode.name)
            setProperty("playerSide", snapshot.playerSide.name)
            snapshot.terminalState?.let { setProperty("terminalState", it.name) }
            snapshot.finishedGame?.let { finished ->
                setProperty("finishedReason", finished.reason.name)
                setProperty("finishedIndex", finished.selectedIndex.toString())
                finished.lastExportPath?.let { setProperty("finishedPath", it) }
            }
            snapshot.promotionPick?.let { promotion ->
                setProperty("promotionBaseMove", promotion.baseMove)
                setProperty(
                    "promotionPieces",
                    promotion.legalPieces.joinToString(SEP) { it.name },
                )
            }
            snapshot.batteryPct?.let { setProperty("batteryPct", it.toString()) }
            setProperty("miniKeyboardVisible", snapshot.miniKeyboardVisible.toString())
        }
        return StringWriter().use { writer ->
            props.store(writer, null)
            writer.toString()
        }
    }

    fun decode(encoded: String): SessionSnapshot? =
        runCatching {
            val props = Properties().apply {
                load(StringReader(encoded))
            }
            if (props.getProperty("version") != VERSION) return@runCatching null
            SessionSnapshot(
                phase = props.readPhase(),
                moveHistory = MoveHistory(
                    props.getProperty("moves")
                        ?.takeIf { it.isNotBlank() }
                        ?.split(SEP)
                        ?.filter { it.isNotBlank() }
                        ?: emptyList(),
                ),
                moveBuffer = props.readMoveBuffer(),
                gameMode = enumValueOrNull<GameMode>(props.getProperty("gameMode"))
                    ?: GameMode.AutoAdvance,
                playerSide = enumValueOrNull<ChessSide>(props.getProperty("playerSide"))
                    ?: ChessSide.WHITE,
                terminalState = enumValueOrNull<TerminalState>(
                    props.getProperty("terminalState"),
                ),
                finishedGame = props.readFinishedGame(),
                promotionPick = props.readPromotionPick(),
                batteryPct = props.getProperty("batteryPct")?.toIntOrNull(),
                miniKeyboardVisible = props.getProperty("miniKeyboardVisible").toBoolean(),
            )
        }.getOrNull()

    private fun Properties.readPhase(): AppPhase =
        when (getProperty("phase")) {
            "inGame" -> AppPhase.InGame
            else -> AppPhase.StartMenu(getProperty("startIndex")?.toIntOrNull() ?: 0)
        }

    private fun MoveBuffer.writeTo(props: Properties) {
        props.setNullableInt("fromFileIdx", fromFileIdx)
        props.setNullableInt("fromRankIdx", fromRankIdx)
        props.setNullableInt("toFileIdx", toFileIdx)
        props.setNullableInt("toRankIdx", toRankIdx)
        props.setProperty("fromFileReadPending", fromFileReadPending.toString())
        props.setProperty("fromRankReadPending", fromRankReadPending.toString())
        props.setProperty("toFileReadPending", toFileReadPending.toString())
        props.setProperty("toRankReadPending", toRankReadPending.toString())
    }

    private fun Properties.readMoveBuffer(): MoveBuffer =
        MoveBuffer(
            fromFileIdx = getNullableInt("fromFileIdx"),
            fromRankIdx = getNullableInt("fromRankIdx"),
            toFileIdx = getNullableInt("toFileIdx"),
            toRankIdx = getNullableInt("toRankIdx"),
            fromFileReadPending = getProperty("fromFileReadPending").toBoolean(),
            fromRankReadPending = getProperty("fromRankReadPending").toBoolean(),
            toFileReadPending = getProperty("toFileReadPending").toBoolean(),
            toRankReadPending = getProperty("toRankReadPending").toBoolean(),
        )

    private fun Properties.readFinishedGame(): FinishedGameUiState? {
        val reason = enumValueOrNull<GameEndReason>(getProperty("finishedReason"))
            ?: return null
        return FinishedGameUiState(
            reason = reason,
            selectedIndex = getProperty("finishedIndex")?.toIntOrNull() ?: 0,
            lastExportPath = getProperty("finishedPath"),
        )
    }

    private fun Properties.readPromotionPick(): PromotionPickState? {
        val baseMove = getProperty("promotionBaseMove") ?: return null
        val pieces = getProperty("promotionPieces")
            ?.split(SEP)
            ?.mapNotNull { enumValueOrNull<PromotionPiece>(it) }
            ?.toSet()
            .orEmpty()
        return pieces
            .takeIf { it.isNotEmpty() }
            ?.let { PromotionPickState(baseMove, it) }
    }

    private fun Properties.setNullableInt(key: String, value: Int?) {
        if (value != null) setProperty(key, value.toString())
    }

    private fun Properties.getNullableInt(key: String): Int? =
        getProperty(key)?.toIntOrNull()

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
