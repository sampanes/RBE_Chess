package com.ratherbeembed.rbe_chess.game

import com.ratherbeembed.rbe_chess.chess.ChessSide
import com.ratherbeembed.rbe_chess.chess.DrawDetector
import com.ratherbeembed.rbe_chess.chess.GameEndReason
import com.ratherbeembed.rbe_chess.chess.GameTextExport
import com.ratherbeembed.rbe_chess.chess.GameTextExporter
import com.ratherbeembed.rbe_chess.chess.MoveHistory
import com.ratherbeembed.rbe_chess.engine.AnalysisSummary
import com.ratherbeembed.rbe_chess.engine.BestMoveResult
import com.ratherbeembed.rbe_chess.engine.StockfishEngine
import com.ratherbeembed.rbe_chess.engine.TerminalState
import com.ratherbeembed.rbe_chess.input.BatteryReportParser
import com.ratherbeembed.rbe_chess.input.BatteryTelemetrySmoother
import com.ratherbeembed.rbe_chess.input.ChessKey
import com.ratherbeembed.rbe_chess.input.GrammarAction
import com.ratherbeembed.rbe_chess.input.HardwareKeyboardHandler
import com.ratherbeembed.rbe_chess.input.KeyboardGrammar
import com.ratherbeembed.rbe_chess.input.MoveBuffer
import com.ratherbeembed.rbe_chess.input.PromotionPickState
import com.ratherbeembed.rbe_chess.session.SessionSnapshot
import com.ratherbeembed.rbe_chess.speech.BestMoveSpeaker
import com.ratherbeembed.rbe_chess.ui.AppPhase
import com.ratherbeembed.rbe_chess.ui.FINISHED_GAME_NEW_GAME
import com.ratherbeembed.rbe_chess.ui.FINISHED_GAME_OPTIONS
import com.ratherbeembed.rbe_chess.ui.FINISHED_GAME_SAVE_EXPORT
import com.ratherbeembed.rbe_chess.ui.FinishedGameUiState
import com.ratherbeembed.rbe_chess.ui.GameMode
import com.ratherbeembed.rbe_chess.ui.START_MENU_OPTIONS
import com.ratherbeembed.rbe_chess.ui.START_MENU_PLAY_WHITE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

internal const val INACTIVITY_PROMPT_MS = 2_500L
internal const val ENGINE_MOVETIME_MS = 4_000L

// Battery TTS-warning thresholds. Falling-edge: speak once when pct
// first dips below the threshold. Rising-edge: above BATTERY_REARM_PCT
// we re-arm so a recharge re-enables a future warning.
private const val BATTERY_LOW_PCT = 20
private const val BATTERY_CRITICAL_PCT = 5
private const val BATTERY_REARM_PCT = 30
internal val MOCK_BATTERY_REPORTS = intArrayOf(88, 19, 4, 3, 73)

/**
 * The whole keypad-driven game loop: start menu, move entry, legality check,
 * engine replies, draw detection, chords, promotion, finished-game menu, and
 * keypad battery.
 *
 * Android-free on purpose: the Activity hands in a coroutine scope, the
 * engine, a speaker, and an export sink, so all of this runs in JVM tests
 * against fakes. [state] is the single source of truth the UI renders.
 */
class GameController(
    private val scope: CoroutineScope,
    private val engine: StockfishEngine,
    private val speaker: BestMoveSpeaker,
    private val saveExport: (GameTextExport) -> String,
    private val log: GameLog = GameLog.NONE,
    val state: GameState = GameState(),
) {
    private val narrative = NarrativeTracker(engine)
    private val batteryParser = BatteryReportParser()
    private val batterySmoother = BatteryTelemetrySmoother(
        lowPct = BATTERY_LOW_PCT,
        criticalPct = BATTERY_CRITICAL_PCT,
        rearmPct = BATTERY_REARM_PCT,
    )
    private var mockBatteryIndex = 0

    private val inactivityJob = JobSlot()
    private val engineJob = JobSlot()
    private val autofill = AutofillCoordinator(scope, engine, state, log, ::applyAutofill)

    // --- Lifecycle -----------------------------------------------------------

    fun restore(snapshot: SessionSnapshot) {
        state.applySnapshot(snapshot)
        narrative.clear()
        narrative.rememberLatestOf(state.moveHistory)
        state.engineStatus =
            if (state.phase == AppPhase.InGame) {
                "Resumed: history=${state.moveHistory.size} plies"
            } else {
                GameState.ENGINE_IDLE
            }
    }

    /** First speech after launch: the resumed game, or the start menu. */
    fun announceStartup(restored: Boolean) {
        if (restored && state.phase == AppPhase.InGame) {
            rememberCurrentPositionForRepeat()
            val finished = state.finishedGame
            if (finished != null) {
                speaker.speakFinishedGame("Resumed game", FINISHED_GAME_OPTIONS[finished.selectedIndex])
            } else {
                speaker.speakMenuOption("Resumed game. ${state.waitingPhrase()}")
            }
            return
        }
        val startMenu = state.phase as? AppPhase.StartMenu ?: return
        speaker.speakMenuOption("Start menu. ${START_MENU_OPTIONS[startMenu.selectedIndex]}")
    }

    fun shutdown() {
        cancelAllJobs()
    }

    // --- Input entry points --------------------------------------------------

    /**
     * Hardware key-down. Battery reports (firmware v5+: 'B' + 3 digits) are
     * filtered before the chess grammar sees them. Returns true if consumed.
     */
    fun onKeyDown(keyCode: Int): Boolean {
        when (val r = batteryParser.consume(keyCode)) {
            BatteryReportParser.Result.NotConsumed -> Unit
            BatteryReportParser.Result.Consumed -> return true
            is BatteryReportParser.Result.Complete -> {
                handleBatteryReport(r.pct)
                return true
            }
        }
        val key = HardwareKeyboardHandler.toChessKey(keyCode)
        if (key == ChessKey.IGNORED) return false
        routeKey(key)
        return true
    }

    /** On-screen mini keypad: same path as the Bluetooth keypad, minus battery parsing. */
    fun onMiniKey(key: ChessKey) {
        if (key != ChessKey.IGNORED) routeKey(key)
    }

    fun onMockBattery() {
        val pct = MOCK_BATTERY_REPORTS[mockBatteryIndex % MOCK_BATTERY_REPORTS.size]
        mockBatteryIndex += 1
        handleBatteryReport(pct)
        state.engineStatus = "Mock battery report: $pct%"
    }

    fun toggleMiniKeyboard() {
        state.miniKeyboardVisible = !state.miniKeyboardVisible
    }

    private fun routeKey(key: ChessKey) {
        when (val phase = state.phase) {
            is AppPhase.StartMenu -> handleMenuKey(phase, key)
            AppPhase.InGame -> handleGameKey(key)
        }
    }

    private fun handleBatteryReport(pct: Int) {
        val update = batterySmoother.record(pct)
        state.batteryPct = update.displayPct
        log.d("Battery report: $pct% -> display=${update.displayPct} warning=${update.warning}")
        when (update.warning) {
            BatteryTelemetrySmoother.Warning.CRITICAL -> speaker.speakBatteryWarning(critical = true)
            BatteryTelemetrySmoother.Warning.LOW -> speaker.speakBatteryWarning(critical = false)
            null -> Unit
        }
    }

    // --- Start menu ----------------------------------------------------------

    private fun handleMenuKey(menu: AppPhase.StartMenu, key: ChessKey) {
        val size = START_MENU_OPTIONS.size
        when (key) {
            ChessKey.F -> selectMenuIndex((menu.selectedIndex - 1 + size) % size)
            ChessKey.J -> selectMenuIndex((menu.selectedIndex + 1) % size)
            ChessKey.SPACE -> startGame(asWhite = menu.selectedIndex == START_MENU_PLAY_WHITE)
            ChessKey.REPEAT_LAST -> speaker.repeatLast()
            // Pinky / Index / other chords are no-ops in the menu.
            else -> Unit
        }
    }

    private fun selectMenuIndex(index: Int) {
        state.phase = AppPhase.StartMenu(index)
        speaker.speakMenuOption(START_MENU_OPTIONS[index])
    }

    private fun startGame(asWhite: Boolean) {
        cancelAllJobs()
        state.clearBoard()
        narrative.clear()
        state.playerSide = if (asWhite) ChessSide.WHITE else ChessSide.BLACK
        state.phase = AppPhase.InGame
        state.engineStatus = if (asWhite) "Engine: opening as white..." else GameState.ENGINE_IDLE
        speaker.speakGameStart(asWhite)
        if (asWhite) bootstrapEngineMove()
    }

    // --- In-game dispatch ----------------------------------------------------

    private fun handleGameKey(key: ChessKey) {
        when (val action = KeyboardGrammar.translate(key)) {
            GrammarAction.Undo -> handleUndo()
            GrammarAction.ToggleManual ->
                if (state.finishedGame == null) handleToggleManual() else speakFinishedSelection()
            GrammarAction.RepeatLast -> {
                val promotion = state.promotionPick
                if (promotion != null) {
                    speaker.speakPromotionPrompt(promotion.baseMove)
                } else {
                    inactivityJob.cancel()
                    speaker.repeatLast(narrative.latest)
                }
            }
            GrammarAction.NewGame ->
                if (state.finishedGame != null) handleNewGame() else endCurrentGame()
            else -> {
                val finished = state.finishedGame
                val terminal = state.terminalState
                when {
                    finished != null -> handleFinishedGameAction(action, finished)
                    terminal != null -> speaker.speakTerminal(terminal)
                    state.promotionPick != null -> handlePromotionKey(key)
                    else -> handleLiveGameAction(action)
                }
            }
        }
    }

    private fun handleLiveGameAction(action: GrammarAction) {
        if (action == GrammarAction.Ignored) return
        if (engineJob.isActive) {
            // While a committed move is being checked / answered, keep the
            // visible buffer stable. Chords such as Undo/New Game are handled
            // before this method and can still cancel the engine job.
            log.d("Input ignored -- engine still calculating")
            return
        }
        when (action) {
            GrammarAction.Commit -> {
                inactivityJob.cancel()
                commitMove(state.moveBuffer.toUciString())
            }
            GrammarAction.CycleFromFile,
            GrammarAction.CycleFromRank,
            GrammarAction.CycleToFile,
            GrammarAction.CycleToRank -> cycleBuffer(action)
            else -> Unit
        }
    }

    private fun cycleBuffer(action: GrammarAction) {
        val before = state.moveBuffer
        val after = KeyboardGrammar.apply(action, before)
        state.moveBuffer = after
        inactivityJob.cancel()
        val isSourceCoordinate =
            action == GrammarAction.CycleFromFile || action == GrammarAction.CycleFromRank
        if (!isSourceCoordinate) autofill.cancel()
        when (action) {
            GrammarAction.CycleFromFile -> speaker.speakFilePress(after.fromFile)
            GrammarAction.CycleFromRank -> speaker.speakRankPress(after.fromRank)
            GrammarAction.CycleToFile -> speaker.speakFilePress(after.toFile)
            GrammarAction.CycleToRank -> speaker.speakRankPress(after.toRank)
            else -> Unit
        }
        log.d("$action -> buffer=${after.toUciString()}")
        scheduleInactivityPrompt(after)
        val sourceChanged =
            before.fromFileIdx != after.fromFileIdx || before.fromRankIdx != after.fromRankIdx
        if (isSourceCoordinate && sourceChanged) autofill.afterSourceSelected(after)
    }

    // --- Finished-game menu --------------------------------------------------

    private fun handleFinishedGameAction(action: GrammarAction, finished: FinishedGameUiState) {
        val size = FINISHED_GAME_OPTIONS.size
        when (action) {
            GrammarAction.CycleFromRank -> selectFinishedIndex(finished, (finished.selectedIndex - 1 + size) % size)
            GrammarAction.CycleToFile -> selectFinishedIndex(finished, (finished.selectedIndex + 1) % size)
            GrammarAction.Commit -> when (finished.selectedIndex) {
                FINISHED_GAME_SAVE_EXPORT -> saveGameExport(finished.reason)
                FINISHED_GAME_NEW_GAME -> handleNewGame()
            }
            GrammarAction.CycleFromFile,
            GrammarAction.CycleToRank,
            GrammarAction.Ignored -> speakFinishedSelection()
            else -> Unit
        }
    }

    private fun selectFinishedIndex(finished: FinishedGameUiState, index: Int) {
        state.finishedGame = finished.copy(selectedIndex = index)
        speaker.speakFinishedGameOption(FINISHED_GAME_OPTIONS[index])
    }

    private fun speakFinishedSelection() {
        val finished = state.finishedGame ?: return
        speaker.speakFinishedGameOption(FINISHED_GAME_OPTIONS[finished.selectedIndex])
    }

    private fun speakFinishedSaveOptionQueued() {
        speaker.speakFinishedGameOption(FINISHED_GAME_OPTIONS[FINISHED_GAME_SAVE_EXPORT], queued = true)
    }

    private fun saveGameExport(reason: GameEndReason) {
        try {
            val path = saveExport(GameTextExporter.build(state.moveHistory, reason))
            state.finishedGame = state.finishedGame?.copy(lastExportPath = path)
            state.engineStatus = "Saved export: $path"
            speaker.speakExportSaved(path)
            log.d("Saved game export: $path")
        } catch (t: Throwable) {
            state.engineStatus = "Export failed: ${t.message}"
            speaker.speakExportFailed()
            log.e("game export failed", t)
        }
    }

    // --- Engine turns --------------------------------------------------------

    /**
     * Play-as-white bootstrap: ask Stockfish for white's opening move. In
     * AutoAdvance it is appended, so the loop is already "ahead by one" when
     * the user types black's reply. In Manual it is spoken and prefilled, and
     * the user still commits or edits it.
     */
    private fun bootstrapEngineMove() {
        launchEngineTurn(onError = { "Engine error on bootstrap: ${it.message}" }) {
            engine.boot()
            when (val result = engine.bestMove(uciMoves = emptyList(), movetimeMs = ENGINE_MOVETIME_MS)) {
                is BestMoveResult.Move -> {
                    val best = result.uci
                    if (state.gameMode == GameMode.AutoAdvance) {
                        applyEngineMove(
                            historyBefore = MoveHistory.EMPTY,
                            move = best,
                            mover = state.playerSide,
                            wasForced = false,
                            beforeAnalysis = narrative.analysisFor(MoveHistory.EMPTY),
                            queued = false,
                            statusPrefix = "Bootstrap: engine=$best",
                        )
                    } else {
                        prefillManualSuggestion(best)
                        speaker.speakSuggestionFor(state.moverLabel(state.playerSide), best)
                        state.engineStatus = "Bootstrap (manual): suggested $best"
                    }
                    log.d("Bootstrap engine move: $best (mode=${state.gameMode})")
                }
                is BestMoveResult.Terminal -> {
                    state.finishWith(result.state)
                    speaker.speakTerminal(result.state)
                    speakFinishedSaveOptionQueued()
                    state.engineStatus = "Bootstrap: ${result.state.label}"
                }
            }
        }
    }

    /**
     * Thumb/Space commit: reject illegal typed moves, pause for a promotion
     * pick when the base move needs one, otherwise hand off to
     * [commitLegalMove]. Illegal moves and engine errors leave the move
     * history untouched so the user can retry.
     */
    private fun commitMove(typedMove: String) {
        autofill.cancel()
        state.pendingMove = typedMove
        val typedMoverLabel = state.moverLabel(state.sideToMove)
        state.engineStatus = "Engine: checking $typedMove..."
        log.d("Check $typedMove legality (history=${state.moveHistory.moves}, mode=${state.gameMode})")
        launchEngineTurn(
            onError = {
                state.promotionPick = null
                "Engine error checking $typedMove: ${it.message}"
            },
        ) {
            engine.boot()
            val legalMoves = engine.legalMoves(state.moveHistory.moves)
            if (typedMove !in legalMoves) {
                state.pendingMove = null
                val promotion = PromotionPickState.fromLegalMoves(typedMove, legalMoves)
                state.promotionPick = promotion
                if (promotion != null) {
                    speaker.speakPromotionPrompt(typedMove)
                    state.engineStatus = "Promotion pending: $typedMove (${promotion.legalPieceNames()})"
                } else {
                    speaker.speakIllegalMove(state.waitingPhrase())
                    state.engineStatus = "Illegal move: $typedMove (history=${state.moveHistory.size} plies)"
                    log.d("Illegal move rejected: $typedMove legal=${legalMoves.sorted()}")
                }
                return@launchEngineTurn
            }
            state.promotionPick = null
            state.moveBuffer = MoveBuffer.DEFAULT
            commitLegalMove(typedMove, typedMoverLabel, wasForced = legalMoves.size == 1)
        }
    }

    private fun handlePromotionKey(key: ChessKey) {
        if (engineJob.isActive) {
            log.d("Promotion input ignored -- engine still calculating")
            return
        }
        val promotion = state.promotionPick ?: return
        val promotedMove = promotion.choose(key)
        if (promotedMove == null) {
            speaker.speakPromotionPrompt(promotion.baseMove)
            state.engineStatus = "Promotion pending: ${promotion.baseMove} (${promotion.legalPieceNames()})"
            return
        }

        autofill.cancel()
        inactivityJob.cancel()
        state.promotionPick = null
        state.moveBuffer = MoveBuffer.DEFAULT
        state.pendingMove = promotedMove
        val typedMoverLabel = state.moverLabel(state.sideToMove)
        state.engineStatus = "Engine: thinking on $promotedMove..."
        log.d("Promotion selected: ${promotion.baseMove} -> $promotedMove")
        launchEngineTurn(onError = { "Engine error after $promotedMove: ${it.message}" }) {
            engine.boot()
            commitLegalMove(promotedMove, typedMoverLabel)
        }
    }

    /**
     * Append a legal typed move, then ask Stockfish for the reply:
     *  - AutoAdvance: the reply is spoken as played and appended too;
     *  - Manual: the reply is spoken and prefilled as a suggestion only.
     */
    private suspend fun commitLegalMove(
        typedMove: String,
        typedMoverLabel: String,
        wasForced: Boolean = false,
    ) {
        val historyBefore = state.moveHistory
        val historyAfterTyped = historyBefore.append(typedMove)
        val mover = sideToMove(historyBefore)
        val nextMover = sideToMove(historyAfterTyped)
        val beforeAnalysis = narrative.analysisFor(historyBefore)
        val typedState = positionStateAfter(historyAfterTyped)
        val typedDraw = DrawDetector.statusFor(historyAfterTyped)
        val afterTypedAnalysis = narrative.analysisFor(historyAfterTyped)

        fun rememberTypedMove(onlyReply: String?) = narrative.rememberMove(
            historyBefore = historyBefore,
            move = typedMove,
            mover = mover,
            wasForced = wasForced,
            onlyReply = onlyReply,
            beforeAnalysis = beforeAnalysis,
            afterAnalysis = afterTypedAnalysis,
        )

        fun finishAfterTypedMove(terminal: TerminalState) {
            state.moveHistory = historyAfterTyped
            state.pendingMove = null
            state.finishWith(terminal)
            state.engineStatus = "${terminal.label} after $typedMove (${state.moveHistory.size} plies)"
            log.d("Terminal after $typedMove: $terminal history=${state.moveHistory.moves}")
        }

        // Checkmate/stalemate take precedence over automatic draws (FIDE).
        val typedTerminal = typedState.terminalState
            ?: typedDraw.automatic?.toTerminalState()
        if (typedTerminal != null) {
            finishAfterTypedMove(typedTerminal)
            rememberTypedMove(typedState.onlyReply)
            speaker.speakPlayedTerminal(typedMoverLabel, typedMove, typedTerminal)
            speakFinishedSaveOptionQueued()
            return
        }

        rememberTypedMove(typedState.onlyReply)
        speaker.speakPlayedThenCalculating(
            typedMoverLabel,
            typedMove,
            state.moverLabel(nextMover),
            givesCheck = typedState.inCheck,
        )
        typedDraw.claimable?.let(speaker::speakDrawClaimAvailable)
        state.engineStatus = "Engine: thinking on $typedMove..."
        log.d("Commit $typedMove (history -> ${historyAfterTyped.moves}, mode=${state.gameMode})")
        try {
            when (val result = engine.bestMove(historyAfterTyped.moves, ENGINE_MOVETIME_MS)) {
                is BestMoveResult.Move -> {
                    val best = result.uci
                    if (state.gameMode == GameMode.Manual) {
                        state.moveHistory = historyAfterTyped
                        state.pendingMove = null
                        speaker.speakPlayedAndSuggestion(
                            typedMoverLabel,
                            typedMove,
                            state.moverLabel(nextMover),
                            best,
                            givesCheck = typedState.inCheck,
                        )
                        state.engineStatus =
                            "Manual: opp=$typedMove (suggested $best, ${state.moveHistory.size} plies)"
                        prefillManualSuggestion(best)
                    } else {
                        applyEngineMove(
                            historyBefore = historyAfterTyped,
                            move = best,
                            mover = nextMover,
                            wasForced = typedState.legalMoves.size == 1,
                            beforeAnalysis = afterTypedAnalysis,
                            queued = true,
                            statusPrefix = "Last: opp=$typedMove -> engine=$best",
                        )
                    }
                }
                is BestMoveResult.Terminal -> {
                    finishAfterTypedMove(result.state)
                    rememberTypedMove(onlyReply = null)
                    speaker.speakTerminal(result.state)
                    speakFinishedSaveOptionQueued()
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            state.pendingMove = null
            state.engineStatus = "Engine error after $typedMove: ${t.message}"
            log.e("engine bestmove failed", t)
        }
    }

    /** AutoAdvance: append an engine-chosen move and announce it (or the game end). */
    private suspend fun applyEngineMove(
        historyBefore: MoveHistory,
        move: String,
        mover: ChessSide,
        wasForced: Boolean,
        beforeAnalysis: AnalysisSummary?,
        queued: Boolean,
        statusPrefix: String,
    ) {
        val historyAfter = historyBefore.append(move)
        val afterState = positionStateAfter(historyAfter)
        val draw = DrawDetector.statusFor(historyAfter)
        val afterAnalysis = narrative.analysisFor(historyAfter)
        state.moveHistory = historyAfter
        state.pendingMove = null
        narrative.rememberMove(
            historyBefore = historyBefore,
            move = move,
            mover = mover,
            wasForced = wasForced,
            onlyReply = afterState.onlyReply,
            beforeAnalysis = beforeAnalysis,
            afterAnalysis = afterAnalysis,
        )
        val terminal = afterState.terminalState ?: draw.automatic?.toTerminalState()
        if (terminal != null) {
            state.finishWith(terminal)
            speaker.speakPlayedTerminal(state.moverLabel(mover), move, terminal, queued = queued)
            speakFinishedSaveOptionQueued()
            state.engineStatus = "$statusPrefix, ${terminal.label}"
            log.d("Terminal after engine move $move: $terminal history=${historyAfter.moves}")
            return
        }
        speaker.speakPlayedMove(
            state.moverLabel(mover),
            move,
            state.waitingPhrase(),
            givesCheck = afterState.inCheck,
            queued = queued,
        )
        draw.claimable?.let(speaker::speakDrawClaimAvailable)
        state.engineStatus = "$statusPrefix (${historyAfter.size} plies)"
        log.d("Engine move $move history=${historyAfter.moves}")
        autofill.afterBoardChange()
    }

    /**
     * Run one engine turn in [engineJob]. Cancellation (undo, new game) is
     * silent; any other failure clears the pending move and shows the error.
     */
    private fun launchEngineTurn(onError: (Throwable) -> String, block: suspend () -> Unit) {
        engineJob.launch(scope) {
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                state.pendingMove = null
                state.engineStatus = onError(t)
                log.e("engine turn failed", t)
            }
        }
    }

    private suspend fun positionStateAfter(history: MoveHistory): PositionState =
        PositionState(
            legalMoves = engine.legalMoves(history.moves),
            inCheck = engine.isSideToMoveInCheck(history.moves),
        )

    // --- Buffer prefill ------------------------------------------------------

    private fun prefillManualSuggestion(uci: String) {
        val after = MoveBuffer.DEFAULT.copyFromEngine(uci)
        state.moveBuffer = after
        scheduleInactivityPrompt(after)
    }

    private fun applyAutofill(uci: String, reason: String, detail: String?) {
        val after = state.moveBuffer.copyFromEngine(uci)
        state.moveBuffer = after
        speaker.speakAutofill(uci, reason)
        scheduleInactivityPrompt(after)
        val detailText = detail?.let { " [$it]" } ?: ""
        state.engineStatus = "$reason: $uci$detailText (history=${state.moveHistory.size} plies)"
        log.d("Autofill: $reason -> $uci$detailText")
    }

    private fun scheduleInactivityPrompt(buffer: MoveBuffer) {
        inactivityJob.launch(scope) {
            delay(INACTIVITY_PROMPT_MS)
            speaker.speakMovePrompt(state.moverLabel(state.sideToMove), buffer)
        }
    }

    // --- Chords --------------------------------------------------------------

    private fun handleUndo() {
        // Cancel any in-flight engine query so its delayed result can't
        // overwrite the rewound state set below.
        cancelAllJobs()
        state.moveHistory = state.moveHistory.undoLastPair()
        state.clearPositionState()
        narrative.invalidateAnalysis()
        speaker.speakUndo(state.waitingPhrase())
        rememberCurrentPositionForRepeat()
        state.engineStatus = "Undo: history=${state.moveHistory.size} plies"
        log.d("Undo -> ${state.moveHistory.moves}")
        autofill.afterBoardChange()
    }

    private fun handleToggleManual() {
        state.gameMode =
            if (state.gameMode == GameMode.AutoAdvance) GameMode.Manual else GameMode.AutoAdvance
        val manual = state.gameMode == GameMode.Manual
        speaker.speakManualMode(manual, state.waitingPhrase())
        state.engineStatus =
            "Mode: ${if (manual) "Manual" else "AutoAdvance"} (history=${state.moveHistory.size} plies)"
    }

    /** Hold+Index during a live game: stop here and offer save/export. */
    private fun endCurrentGame() {
        cancelAllJobs()
        state.pendingMove = null
        state.promotionPick = null
        state.terminalState = null
        state.finishedGame = FinishedGameUiState(GameEndReason.FORFEIT)
        state.engineStatus = "Game ended: save/export available"
        speaker.speakFinishedGame(GameEndReason.FORFEIT.label, FINISHED_GAME_OPTIONS[0])
        log.d("Game manually ended at history=${state.moveHistory.moves}")
    }

    private fun handleNewGame() {
        cancelAllJobs()
        state.clearBoard()
        narrative.clear()
        state.phase = AppPhase.StartMenu(0)
        state.engineStatus = GameState.ENGINE_IDLE
        speaker.speakMenuOption("New game. ${START_MENU_OPTIONS[0]}")
    }

    // --- Helpers -------------------------------------------------------------

    private fun rememberCurrentPositionForRepeat() {
        val history = state.moveHistory
        narrative.rememberLatestOf(history)
        val lastMove = history.moves.lastOrNull()
        val mover = lastMover(history)
        if (lastMove == null || mover == null) {
            speaker.rememberBoardAtStart(state.waitingPhrase())
        } else {
            speaker.rememberPlayedMove(state.moverLabel(mover), lastMove, state.waitingPhrase())
        }
    }

    private fun cancelAllJobs() {
        inactivityJob.cancel()
        engineJob.cancel()
        autofill.cancel()
    }
}
