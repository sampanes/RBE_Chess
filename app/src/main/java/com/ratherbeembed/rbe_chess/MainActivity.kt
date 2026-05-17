package com.ratherbeembed.rbe_chess

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.BestMoveResult
import com.ratherbeembed.rbe_chess.engine.StockfishProcessEngine
import com.ratherbeembed.rbe_chess.engine.TerminalState
import com.ratherbeembed.rbe_chess.input.BatteryReportParser
import com.ratherbeembed.rbe_chess.input.BatteryTelemetrySmoother
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.GrammarAction
import com.ratherbeembed.rbe_chess.input.HardwareKeyboardHandler
import com.ratherbeembed.rbe_chess.input.KeyboardGrammar
import com.ratherbeembed.rbe_chess.input.MoveAutofill
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.pocket.PocketModeController
import com.ratherbeembed.rbe_chess.pocket.PocketModeState
import com.ratherbeembed.rbe_chess.speech.BestMoveSpeaker
import com.ratherbeembed.rbe_chess.speech.SpeechOutput
import com.ratherbeembed.rbe_chess.ui.AppPhase
import com.ratherbeembed.rbe_chess.ui.AppRoot
import com.ratherbeembed.rbe_chess.ui.GameMode
import com.ratherbeembed.rbe_chess.ui.START_MENU_OPTIONS
import com.ratherbeembed.rbe_chess.ui.START_MENU_PLAY_WHITE
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "RBE_CHESS"
private const val INACTIVITY_PROMPT_MS = 2_500L
private const val ENGINE_MOVETIME_MS = 4_000L
private const val AUTOFILL_MOVETIME_MS = 600L
private const val SOURCE_AUTOFILL_DELAY_MS = INACTIVITY_PROMPT_MS
private const val AUTOFILL_SCORE_MARGIN_CP = 100

// Battery TTS-warning thresholds. Falling-edge: speak once when pct
// first dips below the threshold. Rising-edge: above [BATTERY_REARM_PCT]
// we re-arm so a recharge re-enables a future warning.
private const val BATTERY_LOW_PCT = 20
private const val BATTERY_CRITICAL_PCT = 5
private const val BATTERY_REARM_PCT = 30
private val MOCK_BATTERY_REPORTS = intArrayOf(88, 19, 4, 3, 73)

class MainActivity : ComponentActivity() {
    private var moveBuffer by mutableStateOf(MoveBuffer.DEFAULT)
    private var pocketMode by mutableStateOf(PocketModeState.Normal)
    private var moveHistory by mutableStateOf(MoveHistory.EMPTY)
    private var pendingMove by mutableStateOf<String?>(null)
    private var engineStatus by mutableStateOf("Engine: idle")
    private var phase by mutableStateOf<AppPhase>(AppPhase.StartMenu(0))
    private var gameMode by mutableStateOf(GameMode.AutoAdvance)
    private var playerSide by mutableStateOf(ChessSide.WHITE)
    private var terminalState by mutableStateOf<TerminalState?>(null)
    private var batteryPct by mutableStateOf<Int?>(null)
    private var miniKeyboardVisible by mutableStateOf(false)
    private var mockBatteryIndex = 0
    private lateinit var speechOutput: SpeechOutput
    private lateinit var speaker: BestMoveSpeaker
    private lateinit var pocketController: PocketModeController
    private lateinit var engine: StockfishProcessEngine
    private val batteryParser = BatteryReportParser()
    private val batterySmoother = BatteryTelemetrySmoother(
        lowPct = BATTERY_LOW_PCT,
        criticalPct = BATTERY_CRITICAL_PCT,
        rearmPct = BATTERY_REARM_PCT,
    )
    private var inactivityJob: Job? = null
    private var engineJob: Job? = null
    private var autofillJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speechOutput = SpeechOutput(this)
        speaker = BestMoveSpeaker(speechOutput)
        pocketController = PocketModeController(this)
        engine = StockfishProcessEngine(this)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                AppRoot(
                    phase = phase,
                    buffer = moveBuffer,
                    pocketMode = pocketMode,
                    history = moveHistory,
                    pendingMove = pendingMove,
                    engineStatus = engineStatus,
                    gameMode = gameMode,
                    playerSide = playerSide,
                    batteryPct = batteryPct,
                    miniKeyboardVisible = miniKeyboardVisible,
                    onToggleMiniKeyboard = { miniKeyboardVisible = !miniKeyboardVisible },
                    onMiniKey = ::injectMiniKey,
                    onMockBattery = ::handleMockBatteryReport,
                    onEnterPocketMode = ::enterPocketMode,
                    onExitPocketMode = ::exitPocketMode,
                )
            }
        }
        // Cold-start announcement: speak the menu intro + first option so
        // the user gets verbal feedback before any keypress.
        val startMenu = phase as? AppPhase.StartMenu
        if (startMenu != null) {
            speaker.speakMenuOption(
                "Start menu. ${START_MENU_OPTIONS[startMenu.selectedIndex]}",
            )
        }
    }

    override fun onDestroy() {
        inactivityJob?.cancel()
        inactivityJob = null
        engineJob?.cancel()
        engineJob = null
        autofillJob?.cancel()
        autofillJob = null
        engine.shutdown()
        pocketController.exit()
        speechOutput.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val key = HardwareKeyboardHandler.toChessKey(event.keyCode)

            // Battery reports (firmware v5: 'B' + 3 digits) are filtered
            // out before the chess grammar sees them. The parser bails
            // gracefully on stray inputs so it can't swallow a chord.
            when (val r = batteryParser.consume(event.keyCode)) {
                BatteryReportParser.Result.NotConsumed -> Unit
                BatteryReportParser.Result.Consumed -> return true
                is BatteryReportParser.Result.Complete -> {
                    handleBatteryReport(r.pct)
                    return true
                }
            }

            if (key != ChessKey.IGNORED) {
                when (val p = phase) {
                    is AppPhase.StartMenu -> handleMenuKey(p, key)
                    AppPhase.InGame -> handleGameKey(key)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBatteryReport(pct: Int) {
        Log.d(TAG, "Battery report: $pct%%")
        val update = batterySmoother.record(pct)
        batteryPct = update.displayPct
        Log.d(TAG, "Battery smoothed: display=${update.displayPct} warning=${update.warning}")
        when (update.warning) {
            BatteryTelemetrySmoother.Warning.CRITICAL ->
                speaker.speakBatteryWarning(critical = true)
            BatteryTelemetrySmoother.Warning.LOW ->
                speaker.speakBatteryWarning(critical = false)
            null -> Unit
        }
    }

    private fun handleMockBatteryReport() {
        val pct = MOCK_BATTERY_REPORTS[mockBatteryIndex % MOCK_BATTERY_REPORTS.size]
        mockBatteryIndex += 1
        handleBatteryReport(pct)
        engineStatus = "Mock battery report: $pct%"
    }

    private fun injectMiniKey(key: ChessKey) {
        if (key == ChessKey.IGNORED) return
        when (val p = phase) {
            is AppPhase.StartMenu -> handleMenuKey(p, key)
            AppPhase.InGame -> handleGameKey(key)
        }
    }

    // --- Start menu navigation ---------------------------------------------

    private fun handleMenuKey(state: AppPhase.StartMenu, key: ChessKey) {
        val size = START_MENU_OPTIONS.size
        when (key) {
            ChessKey.F -> {
                // Up. Wrap from index 0 back to the last option.
                val next = (state.selectedIndex - 1 + size) % size
                phase = AppPhase.StartMenu(next)
                speaker.speakMenuOption(START_MENU_OPTIONS[next])
            }
            ChessKey.J -> {
                val next = (state.selectedIndex + 1) % size
                phase = AppPhase.StartMenu(next)
                speaker.speakMenuOption(START_MENU_OPTIONS[next])
            }
            ChessKey.SPACE -> selectMenuOption(state.selectedIndex)
            ChessKey.REPEAT_LAST -> speaker.repeatLast()
            // Pinky / Index / other chord keys are no-ops in menu.
            else -> Unit
        }
    }

    private fun selectMenuOption(idx: Int) {
        val asWhite = (idx == START_MENU_PLAY_WHITE)
        inactivityJob?.cancel(); inactivityJob = null
        engineJob?.cancel(); engineJob = null
        autofillJob?.cancel(); autofillJob = null
        moveHistory = MoveHistory.EMPTY
        pendingMove = null
        moveBuffer = MoveBuffer.DEFAULT
        terminalState = null
        playerSide = if (asWhite) ChessSide.WHITE else ChessSide.BLACK
        phase = AppPhase.InGame
        engineStatus = if (asWhite) "Engine: opening as white..." else "Engine: idle"
        speaker.speakGameStart(asWhite)
        if (asWhite) {
            bootstrapEngineMove()
        }
    }

    /**
     * Play-as-white bootstrap: query Stockfish on an empty position so it
     * speaks white's opening move. In AutoAdvance mode the move is also
     * appended to [moveHistory] so the loop is already "ahead by one" when
     * the user types black's response. In Manual mode the suggestion is
     * spoken and prefilled, but the user still commits or edits it.
     */
    private fun bootstrapEngineMove() {
        engineJob = lifecycleScope.launch {
            try {
                engine.boot()
                when (val result = engine.bestMove(
                    uciMoves = emptyList(),
                    movetimeMs = ENGINE_MOVETIME_MS,
                )) {
                    is BestMoveResult.Move -> {
                        val best = result.uci
                        if (gameMode == GameMode.AutoAdvance) {
                            val nextHistory = MoveHistory.EMPTY.append(best)
                            val terminal = terminalStateAfter(nextHistory)
                            moveHistory = nextHistory
                            if (terminal != null) {
                                terminalState = terminal
                                speaker.speakPlayedTerminal(moverLabel(playerSide), best, terminal)
                                engineStatus = "Bootstrap: engine=$best, ${terminalLabel(terminal)}"
                            } else {
                                val givesCheck = engine.isSideToMoveInCheck(nextHistory.moves)
                                speaker.speakPlayedMove(
                                    moverLabel(playerSide),
                                    best,
                                    waitingPhrase(),
                                    givesCheck = givesCheck,
                                )
                                engineStatus = "Bootstrap: engine=$best (${moveHistory.size} plies)"
                                prefillLegalOrClearEngineMove()
                            }
                        } else {
                            prefillManualSuggestion(best)
                            speaker.speakSuggestionFor(moverLabel(playerSide), best)
                            engineStatus = "Bootstrap (manual): suggested $best"
                        }
                        Log.d(TAG, "Bootstrap engine move: $best (mode=$gameMode)")
                    }
                    is BestMoveResult.Terminal -> {
                        terminalState = result.state
                        speaker.speakTerminal(result.state)
                        engineStatus = "Bootstrap: ${terminalLabel(result.state)}"
                        Log.d(TAG, "Bootstrap terminal: ${result.state}")
                    }
                }
            } catch (t: Throwable) {
                engineStatus = "Engine error on bootstrap: ${t.message}"
                Log.e(TAG, "bootstrap failed", t)
            }
        }
    }

    // --- In-game dispatch --------------------------------------------------

    private fun handleGameKey(key: ChessKey) {
        val action = KeyboardGrammar.translate(key)
        when (action) {
            GrammarAction.Undo -> handleUndo()
            GrammarAction.ToggleManual -> handleToggleManual()
            GrammarAction.RepeatLast -> handleRepeatLast()
            GrammarAction.NewGame -> handleNewGame()
            else -> {
                terminalState?.let {
                    speaker.speakTerminal(it)
                    return
                }
                handleLiveGameAction(action)
            }
        }
    }

    private fun handleLiveGameAction(action: GrammarAction) {
        if (
            engineJob?.isActive == true &&
            action != GrammarAction.Ignored
        ) {
            // While a committed move is being checked / answered, keep the
            // visible buffer stable. Chords such as Undo/New Game are handled
            // before this method and can still cancel the engine job.
            Log.d(TAG, "Input ignored — engine still calculating")
            return
        }
        when (action) {
            GrammarAction.Commit -> {
                autofillJob?.cancel()
                autofillJob = null
                inactivityJob?.cancel()
                inactivityJob = null
                commitMove(moveBuffer.toUciString())
            }
            GrammarAction.CycleFromFile,
            GrammarAction.CycleFromRank,
            GrammarAction.CycleToFile,
            GrammarAction.CycleToRank -> {
                val before = moveBuffer
                val after = KeyboardGrammar.apply(action, before)
                moveBuffer = after
                handleAction(action, before, after)
            }
            GrammarAction.Ignored -> Unit
            GrammarAction.Undo,
            GrammarAction.ToggleManual,
            GrammarAction.RepeatLast,
            GrammarAction.NewGame -> Unit
        }
    }

    private fun handleAction(action: GrammarAction, before: MoveBuffer, after: MoveBuffer) {
        inactivityJob?.cancel()
        inactivityJob = null
        when (action) {
            GrammarAction.CycleFromFile -> {
                speaker.speakFilePress(after.fromFile)
                Log.d(TAG, "from-file -> ${after.fromFile} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
                if (before.fromFileIdx != after.fromFileIdx) {
                    autofillJob?.cancel()
                    autofillJob = null
                    maybeAutofillForSelectedSource(after)
                }
            }
            GrammarAction.CycleFromRank -> {
                speaker.speakRankPress(after.fromRank)
                Log.d(TAG, "from-rank -> ${after.fromRank} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
                if (before.fromRankIdx != after.fromRankIdx) {
                    autofillJob?.cancel()
                    autofillJob = null
                    maybeAutofillForSelectedSource(after)
                }
            }
            GrammarAction.CycleToFile -> {
                autofillJob?.cancel()
                autofillJob = null
                speaker.speakFilePress(after.toFile)
                Log.d(TAG, "to-file -> ${after.toFile} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
            }
            GrammarAction.CycleToRank -> {
                autofillJob?.cancel()
                autofillJob = null
                speaker.speakRankPress(after.toRank)
                Log.d(TAG, "to-rank -> ${after.toRank} buffer=${after.toUciString()}")
                scheduleInactivityPrompt(after)
            }
            GrammarAction.Commit -> {
                commitMove(before.toUciString())
            }
            else -> Unit
        }
    }

    /**
     * Space commit: boot the engine if needed, reject illegal typed moves,
     * ask Stockfish for the bestmove given (history + opponent move), and act
     * on the reply according to [gameMode]:
     *   - AutoAdvance: speak as bestmove, append both plies to [moveHistory].
     *   - Manual:      speak/prefill as suggestion, append only the typed ply.
     * Illegal moves and engine errors leave [moveHistory] untouched so
     * the user can retry without a corrupt move list.
     */
    private fun commitMove(opponentMove: String) {
        autofillJob?.cancel()
        autofillJob = null
        pendingMove = opponentMove
        val mover = sideToMove()
        val typedMoverLabel = moverLabel(mover)
        engineStatus = "Engine: checking $opponentMove..."
        Log.d(TAG, "Check $opponentMove legality (history=${moveHistory.moves}, mode=$gameMode)")
        engineJob = lifecycleScope.launch {
            try {
                engine.boot()
                val legalMoves = engine.legalMoves(moveHistory.moves)
                if (opponentMove !in legalMoves) {
                    pendingMove = null
                    speaker.speakIllegalMove(waitingPhrase())
                    engineStatus = "Illegal move: $opponentMove (history=${moveHistory.size} plies)"
                    Log.d(TAG, "Illegal move rejected: $opponentMove legal=${legalMoves.sorted()}")
                    return@launch
                }

                moveBuffer = MoveBuffer.DEFAULT
                commitLegalMove(opponentMove, typedMoverLabel)
            } catch (t: Throwable) {
                pendingMove = null
                engineStatus = "Engine error checking $opponentMove: ${t.message}"
                Log.e(TAG, "engine legal move check failed", t)
            }
        }
    }

    private suspend fun commitLegalMove(opponentMove: String, typedMoverLabel: String) {
        val historyForEngine = moveHistory.append(opponentMove)
        val nextMover = sideToMove(historyForEngine)
        val typedTerminal = terminalStateAfter(historyForEngine)
        if (typedTerminal != null) {
            moveHistory = historyForEngine
            pendingMove = null
            terminalState = typedTerminal
            speaker.speakPlayedTerminal(typedMoverLabel, opponentMove, typedTerminal)
            engineStatus =
                "${terminalLabel(typedTerminal)} after $opponentMove (${moveHistory.size} plies)"
            Log.d(TAG, "Terminal after $opponentMove: $typedTerminal history=${moveHistory.moves}")
            return
        }

        val typedMoveGivesCheck = engine.isSideToMoveInCheck(historyForEngine.moves)
        speaker.speakPlayedThenCalculating(
            typedMoverLabel,
            opponentMove,
            moverLabel(nextMover),
            givesCheck = typedMoveGivesCheck,
        )
        engineStatus = "Engine: thinking on $opponentMove..."
        Log.d(TAG, "Commit $opponentMove (history -> ${historyForEngine.moves}, mode=$gameMode)")
        try {
            when (val result = engine.bestMove(
                uciMoves = historyForEngine.moves,
                movetimeMs = ENGINE_MOVETIME_MS,
            )) {
                is BestMoveResult.Move -> {
                    val best = result.uci
                    if (gameMode == GameMode.Manual) {
                        moveHistory = historyForEngine
                        pendingMove = null
                        speaker.speakPlayedAndSuggestion(
                            typedMoverLabel,
                            opponentMove,
                            moverLabel(nextMover),
                            best,
                            givesCheck = typedMoveGivesCheck,
                        )
                        engineStatus =
                            "Manual: opp=$opponentMove (suggested $best, ${moveHistory.size} plies)"
                        Log.d(TAG, "Step 4 (manual): opp=$opponentMove suggested=$best")
                        prefillManualSuggestion(best)
                    } else {
                        val replyHistory = historyForEngine.append(best)
                        val terminal = terminalStateAfter(replyHistory)
                        moveHistory = replyHistory
                        pendingMove = null
                        if (terminal != null) {
                            terminalState = terminal
                            speaker.speakPlayedTerminal(
                                moverLabel(nextMover),
                                best,
                                terminal,
                                queued = true,
                            )
                            engineStatus =
                                "Last: opp=$opponentMove -> engine=$best, ${terminalLabel(terminal)}"
                            Log.d(TAG, "Terminal after engine reply $best: $terminal history=${moveHistory.moves}")
                            return
                        }

                        val replyGivesCheck = engine.isSideToMoveInCheck(replyHistory.moves)
                        speaker.speakPlayedMove(
                            moverLabel(nextMover),
                            best,
                            waitingPhrase(),
                            givesCheck = replyGivesCheck,
                            queued = true,
                        )
                        engineStatus =
                            "Last: opp=$opponentMove -> engine=$best (${moveHistory.size} plies)"
                        Log.d(TAG, "Step 4 reply: opp=$opponentMove eng=$best history=${moveHistory.moves}")
                        prefillLegalOrClearEngineMove()
                    }
                }
                is BestMoveResult.Terminal -> {
                    moveHistory = historyForEngine
                    pendingMove = null
                    terminalState = result.state
                    speaker.speakTerminal(result.state)
                    engineStatus =
                        "${terminalLabel(result.state)} after $opponentMove (${moveHistory.size} plies)"
                    Log.d(TAG, "Terminal after $opponentMove: ${result.state} history=${moveHistory.moves}")
                }
            }
        } catch (t: Throwable) {
            pendingMove = null
            engineStatus = "Engine error after $opponentMove: ${t.message}"
            Log.e(TAG, "engine bestmove failed", t)
        }
    }

    // --- Autocomplete ------------------------------------------------------

    private fun prefillLegalOrClearEngineMove() {
        val historyAtRequest = moveHistory
        val bufferAtRequest = moveBuffer
        autofillJob?.cancel()
        autofillJob = lifecycleScope.launch {
            try {
                engine.boot()
                val legalMoves = engine.legalMoves(historyAtRequest.moves)
                val forced = MoveAutofill.onlyLegalMove(legalMoves)
                if (forced != null) {
                    if (
                        phase == AppPhase.InGame &&
                        terminalState == null &&
                        moveHistory == historyAtRequest &&
                        moveBuffer == bufferAtRequest
                    ) {
                        applyAutofill(forced, "Only legal move")
                    }
                    return@launch
                }

                val suggestion = clearEngineSuggestion(historyAtRequest.moves, legalMoves)
                    ?: return@launch
                if (
                    phase == AppPhase.InGame &&
                    terminalState == null &&
                    moveHistory == historyAtRequest &&
                    moveBuffer == bufferAtRequest
                ) {
                    applyAutofill(
                        suggestion.uci,
                        "Suggestion",
                        "gap ${suggestion.scoreGapCp} cp",
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "post-move autofill failed", t)
            }
        }
    }

    private fun maybeAutofillForSelectedSource(bufferAtRequest: MoveBuffer) {
        val fromSquare = bufferAtRequest.fromSquareOrNull ?: return
        val historyAtRequest = moveHistory
        autofillJob?.cancel()
        autofillJob = lifecycleScope.launch {
            try {
                delay(SOURCE_AUTOFILL_DELAY_MS)
                if (
                    phase != AppPhase.InGame ||
                    terminalState != null ||
                    moveHistory != historyAtRequest ||
                    moveBuffer != bufferAtRequest
                ) {
                    return@launch
                }

                engine.boot()
                val legalMoves = engine.legalMoves(historyAtRequest.moves)
                val candidates = legalMoves
                    .filter { it.length >= 4 && it.startsWith(fromSquare) }
                    .toSet()
                val forced = MoveAutofill.onlyLegalMove(candidates)
                if (forced != null) {
                    if (
                        phase == AppPhase.InGame &&
                        terminalState == null &&
                        moveHistory == historyAtRequest &&
                        moveBuffer == bufferAtRequest
                    ) {
                        applyAutofill(forced, "Only move from selected piece")
                    }
                    return@launch
                }

                val suggestion = clearEngineSuggestion(historyAtRequest.moves, candidates)
                    ?: return@launch
                if (
                    phase == AppPhase.InGame &&
                    terminalState == null &&
                    moveHistory == historyAtRequest &&
                    moveBuffer == bufferAtRequest
                ) {
                    applyAutofill(
                        suggestion.uci,
                        "Suggestion",
                        "gap ${suggestion.scoreGapCp} cp",
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "source-move autofill failed", t)
            }
        }
    }

    private fun prefillManualSuggestion(uci: String) {
        val after = MoveBuffer.DEFAULT.copyFromEngine(uci)
        moveBuffer = after
        inactivityJob?.cancel()
        inactivityJob = null
        scheduleInactivityPrompt(after)
    }

    private suspend fun terminalStateAfter(history: MoveHistory): TerminalState? {
        if (engine.legalMoves(history.moves).isNotEmpty()) return null
        return if (engine.isSideToMoveInCheck(history.moves)) {
            TerminalState.CHECKMATE
        } else {
            TerminalState.STALEMATE
        }
    }

    private suspend fun clearEngineSuggestion(
        uciMoves: List<String>,
        candidates: Set<String>,
    ) =
        if (candidates.size < 2) {
            null
        } else {
            MoveAutofill.clearBestScoredMove(
                scoredMoves = engine.scoredMoves(
                    uciMoves = uciMoves,
                    candidates = candidates,
                    movetimeMs = AUTOFILL_MOVETIME_MS,
                ),
                minimumScoreGapCp = AUTOFILL_SCORE_MARGIN_CP,
            )
        }

    private fun applyAutofill(uci: String, reason: String, detail: String? = null) {
        val after = moveBuffer.copyFromEngine(uci)
        moveBuffer = after
        speaker.speakAutofill(uci, reason)
        inactivityJob?.cancel()
        inactivityJob = null
        scheduleInactivityPrompt(after)
        val detailText = detail?.let { " [$it]" } ?: ""
        engineStatus = "$reason: $uci$detailText (history=${moveHistory.size} plies)"
        Log.d(TAG, "Autofill: $reason -> $uci$detailText")
    }

    // --- Chord handlers ----------------------------------------------------

    private fun handleUndo() {
        inactivityJob?.cancel(); inactivityJob = null
        autofillJob?.cancel(); autofillJob = null
        // Cancel any in-flight engine query so its delayed result can't
        // overwrite the rewound state we're about to set below.
        engineJob?.cancel(); engineJob = null
        moveHistory = moveHistory.undoLastPair()
        pendingMove = null
        moveBuffer = MoveBuffer.DEFAULT
        terminalState = null
        speaker.speakUndo(waitingPhrase())
        rememberCurrentPositionForRepeat()
        engineStatus = "Undo: history=${moveHistory.size} plies"
        Log.d(TAG, "Undo -> ${moveHistory.moves}")
        prefillLegalOrClearEngineMove()
    }

    private fun handleToggleManual() {
        gameMode = if (gameMode == GameMode.AutoAdvance) GameMode.Manual else GameMode.AutoAdvance
        val on = (gameMode == GameMode.Manual)
        speaker.speakManualMode(on, waitingPhrase())
        engineStatus = "Mode: ${if (on) "Manual" else "AutoAdvance"} (history=${moveHistory.size} plies)"
        Log.d(TAG, "Manual mode toggled -> $gameMode")
    }

    private fun handleNewGame() {
        inactivityJob?.cancel(); inactivityJob = null
        engineJob?.cancel(); engineJob = null
        autofillJob?.cancel(); autofillJob = null
        moveHistory = MoveHistory.EMPTY
        pendingMove = null
        moveBuffer = MoveBuffer.DEFAULT
        terminalState = null
        phase = AppPhase.StartMenu(0)
        engineStatus = "Engine: idle"
        speaker.speakMenuOption("New game. ${START_MENU_OPTIONS[0]}")
        Log.d(TAG, "New game -> StartMenu")
    }

    private fun handleRepeatLast() {
        inactivityJob?.cancel(); inactivityJob = null
        speaker.repeatLast()
        Log.d(TAG, "Repeat last spoken output")
    }

    // --- Misc --------------------------------------------------------------

    private fun rememberCurrentPositionForRepeat() {
        val lastMove = moveHistory.moves.lastOrNull()
        if (lastMove == null) {
            speaker.rememberBoardAtStart(waitingPhrase())
            return
        }

        val lastMover = if ((moveHistory.size - 1) % 2 == 0) ChessSide.WHITE else ChessSide.BLACK
        speaker.rememberPlayedMove(moverLabel(lastMover), lastMove, waitingPhrase())
    }

    private fun scheduleInactivityPrompt(buffer: MoveBuffer) {
        inactivityJob = lifecycleScope.launch {
            delay(INACTIVITY_PROMPT_MS)
            speaker.speakMovePrompt(moverLabel(sideToMove()), buffer)
        }
    }

    private fun sideToMove(history: MoveHistory = moveHistory): ChessSide =
        if (history.size % 2 == 0) ChessSide.WHITE else ChessSide.BLACK

    private fun moverLabel(side: ChessSide): String {
        val color = if (side == ChessSide.WHITE) "White" else "Black"
        return if (side == playerSide) "Your $color" else "Opponent $color"
    }

    private fun terminalLabel(state: TerminalState): String =
        when (state) {
            TerminalState.CHECKMATE -> "Checkmate"
            TerminalState.STALEMATE -> "Stalemate"
        }

    private fun waitingPhrase(): String = "Waiting for ${moverLabel(sideToMove())}."

    private fun enterPocketMode() {
        pocketController.enter()
        pocketMode = PocketModeState.Pocket
        Log.d(TAG, "Pocket Mode ON")
    }

    private fun exitPocketMode() {
        pocketController.exit()
        pocketMode = PocketModeState.Normal
        Log.d(TAG, "Pocket Mode OFF")
    }
}
