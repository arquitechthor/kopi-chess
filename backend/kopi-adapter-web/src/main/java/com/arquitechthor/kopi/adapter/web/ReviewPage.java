package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.analysis.AnalysisView;
import com.arquitechthor.kopi.application.game.GameView;
import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.domain.analysis.AnalysisSummary;
import com.arquitechthor.kopi.domain.analysis.Evaluation;
import com.arquitechthor.kopi.domain.analysis.GameAnalysis;
import com.arquitechthor.kopi.domain.analysis.MoveCategory;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Opening;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/// What the review page shows (plan §8.5), prepared for the template.
///
/// Until the analysis is done, only its [#state] matters: the page shows a
/// progress bar that follows the analysis live. Once done, everything else
/// is filled in: the summary, the graph, the move list with a verdict for
/// each move, and the positions for the board to step through.
///
/// @param id          the game
/// @param botProfile  the opponent; `null` for an imported game
/// @param players     an imported game's players, `White – Black`; `null`
///                    for a game played here
/// @param orientation `white` or `black`: the person's side at the bottom
/// @param state       `QUEUED`, `RUNNING`, `DONE` or `FAILED`
/// @param error       why it failed, if it did
/// @param opening     the last named opening, if any (and once done)
/// @param engine      `Stockfish 19`
/// @param white       White's accuracy and counts
/// @param black       Black's
/// @param graph       the evaluation graph
/// @param moveRows    the moves, one row per move number
/// @param plies       one verdict per move, in order
/// @param boardJson   the positions for the board: start, then after each
///                    move, with the move that led there and the engine's
///                    choice from there
record ReviewPage(
        String id,
        BotProfile botProfile,
        String players,
        String orientation,
        AnalysisJob.State state,
        String error,
        Opening opening,
        String engine,
        Side white,
        Side black,
        EvalGraph graph,
        List<MoveRow> moveRows,
        List<Ply> plies,
        String boardJson) {

    /// How many moves of the engine's line are shown: enough to see the
    /// idea. The engine's whole line runs to twenty or thirty moves.
    static final int SHOWN_LINE = 6;

    /// The order categories are listed in the summary.
    static final List<MoveCategory> CATEGORIES = List.of(MoveCategory.BOOK, MoveCategory.BEST, MoveCategory.GOOD,
            MoveCategory.INACCURACY, MoveCategory.MISTAKE, MoveCategory.BLUNDER, MoveCategory.MISSED_MATE);

    /// The summary's columns, in order.
    public List<MoveCategory> categories() {
        return CATEGORIES;
    }

    /// One side's summary.
    ///
    /// @param accuracy 0–100, or `null` if it never moved
    /// @param counts   how many moves of each category, in [#CATEGORIES]
    ///                 order
    record Side(Double accuracy, List<Integer> counts) {}

    /// A move in the move list: its SAN (with figurines) and its category.
    record MoveCell(int ply, String san, MoveCategory category) {}

    /// `12. e4 e5`; `black` is `null` when White's move is the last.
    record MoveRow(int number, MoveCell white, MoveCell black) {}

    /// The verdict on one move, ready to read.
    ///
    /// @param ply        the move
    /// @param label      `12.` or `12…`, then the move: `12… Nf6`
    /// @param category   what it was worth
    /// @param evaluation the position after it: `+0.35`, `#3`, `1–0`
    /// @param bestMove   the engine's move instead, in SAN (with figurines)
    /// @param bestLine   the engine's line from there, in SAN (with figurines),
    ///                   cut after [#SHOWN_LINE] moves; a missed mate's in full
    /// @param played     whether the move played was the engine's
    /// @param missedMate for a missed mate, the mate's length, else `null`
    record Ply(int ply, String label, MoveCategory category, String evaluation, String bestMove, String bestLine,
               boolean played, Integer missedMate) {}

    /// The review of a game whose analysis exists (queued, running, failed or
    /// done).
    static ReviewPage of(GameView game, AnalysisView view) {
        var orientation = game.humanColor().orElse(Color.WHITE) == Color.WHITE ? "white" : "black";
        var bot = game.botProfile().orElse(null);
        var players = game.origin() == GameOrigin.IMPORTED ? GamePage.players(game) : null;
        if (view.analysis().isEmpty()) {
            var job = view.job().orElseThrow();
            return new ReviewPage(game.id().toString(), bot, players, orientation, job.state(),
                    job.lastError().orElse(null), null, null, null, null, null, List.of(), List.of(), "[]");
        }
        var analysis = view.analysis().get();
        var summary = analysis.summary();
        return new ReviewPage(game.id().toString(), bot, players, orientation, AnalysisJob.State.DONE, null,
                analysis.opening().orElse(null), analysis.engine(), side(summary.white()), side(summary.black()),
                graph(analysis), rows(view, analysis), plies(view, analysis), boardJson(view, analysis));
    }

    private static Side side(AnalysisSummary.Side side) {
        return new Side(side.accuracy().isPresent() ? side.accuracy().getAsDouble() : null,
                CATEGORIES.stream().map(side::count).toList());
    }

    private static EvalGraph graph(GameAnalysis analysis) {
        var positions = new ArrayList<Evaluation>();
        positions.add(analysis.moves().getFirst().before());
        analysis.moves().forEach(move -> positions.add(move.after()));
        return EvalGraph.of(positions, analysis.moves().stream().map(move -> move.classification().category()).toList());
    }

    /// Move numbers count from White's first move; a game set up with Black
    /// to move starts with `1… e5`.
    private static List<MoveRow> rows(AnalysisView view, GameAnalysis analysis) {
        var cells = new ArrayList<MoveCell>();
        for (var move : analysis.moves()) {
            cells.add(new MoveCell(move.ply(), Figurines.of(view.moves().get(move.ply() - 1).san()),
                    move.classification().category()));
        }
        var rows = new ArrayList<MoveRow>();
        var i = 0;
        var number = 1;
        if (analysis.firstMover() == Color.BLACK) {
            rows.add(new MoveRow(number++, null, cells.getFirst()));
            i = 1;
        }
        for (; i < cells.size(); i += 2) {
            rows.add(new MoveRow(number++, cells.get(i), i + 1 < cells.size() ? cells.get(i + 1) : null));
        }
        return rows;
    }

    private static List<Ply> plies(AnalysisView view, GameAnalysis analysis) {
        var plies = new ArrayList<Ply>();
        for (var move : analysis.moves()) {
            var played = view.moves().get(move.ply() - 1);
            var white = mover(analysis, move.ply()) == Color.WHITE;
            var number = moveNumber(analysis, move.ply());
            var classification = move.classification();
            plies.add(new Ply(move.ply(), number + (white ? ". " : "… ") + Figurines.of(played.san()),
                    classification.category(), evaluation(move.after()), Figurines.of(move.bestLine().getFirst()),
                    line(move.bestLine(), classification.missedMate().isPresent()),
                    played.uci().equals(move.bestMove()),
                    classification.missedMate().isPresent() ? classification.missedMate().getAsInt() : null));
        }
        return plies;
    }

    /// The line as shown: a mate in full (it is the proof), anything else
    /// cut short.
    static String line(List<String> san, boolean mate) {
        var shown = mate || san.size() <= SHOWN_LINE ? san : san.subList(0, SHOWN_LINE);
        var text = shown.stream().map(Figurines::of).collect(Collectors.joining(" "));
        return shown.size() < san.size() ? text + " …" : text;
    }

    private static Color mover(GameAnalysis analysis, int ply) {
        return ply % 2 == 1 ? analysis.firstMover() : analysis.firstMover().opposite();
    }

    private static int moveNumber(GameAnalysis analysis, int ply) {
        var offset = analysis.firstMover() == Color.BLACK ? 1 : 0;
        return (ply + offset + 1) / 2;
    }

    /// An evaluation as players read it, from White's point of view:
    /// `+0.35`, `-1.20`, `#3` (White mates in three), `#-2` (Black mates
    /// in two), `1–0` / `0–1` once mate is on the board.
    static String evaluation(Evaluation evaluation) {
        return switch (evaluation) {
            case Evaluation.Centipawns(var cp) -> String.format(Locale.ROOT, "%+.2f", cp / 100.0);
            case Evaluation.MateIn(var moves) -> "#" + moves;
            case Evaluation.Checkmate(var winner) -> winner == Color.WHITE ? "1–0" : "0–1";
        };
    }

    /// `[{"fen": …, "lastMove": …, "bestMove": …}, …]`: what the board shows
    /// at each step. `lastMove` is the move that led there (empty at the
    /// start), `bestMove` the engine's choice from there (empty at the end).
    private static String boardJson(AnalysisView view, GameAnalysis analysis) {
        var steps = new ArrayList<String>();
        var moves = analysis.moves();
        steps.add(step(view.initialFen(), "", moves.getFirst().bestMove()));
        for (int i = 0; i < view.moves().size(); i++) {
            var played = view.moves().get(i);
            var next = i + 1 < moves.size() ? moves.get(i + 1).bestMove() : "";
            steps.add(step(played.fenAfter(), played.uci(), next));
        }
        return steps.stream().collect(Collectors.joining(",", "[", "]"));
    }

    /// FENs and UCI moves have no quotes or backslashes: no escaping needed.
    private static String step(String fen, String lastMove, String bestMove) {
        return "{\"fen\":\"" + fen + "\",\"lastMove\":\"" + lastMove + "\",\"bestMove\":\"" + bestMove + "\"}";
    }
}
