package com.arquitechthor.kopi.application.game;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.Pgn;
import com.arquitechthor.kopi.domain.chess.PgnGame;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameStatus;
import com.arquitechthor.kopi.domain.game.PlayedMove;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Objects;

/// [ExportPgnUseCase]: the game with the standard tags any chess program
/// expects.
///
/// ```
/// [Event "Kopi Chess"]
/// [Site "Kopi Chess"]
/// [Date "2026.10.26"]
/// [Round "-"]
/// [White "Player"]
/// [Black "Kopi Pawn"]
/// [Result "0-1"]
/// [Termination "normal"]
/// [PlyCount "4"]
///
/// 1. f3 e5 2. g4 Qh4# 0-1
/// ```
///
/// The first seven are PGN's Seven Tag Roster, in its order. `Termination`
/// says `normal` for a finished game (checkmate, resignation, a draw by the
/// rules) and `unterminated` for one still being played, whose result is
/// `*`. The tags are in English whatever the visitor's language: PGN is an
/// exchange format. An imported game is exported with the tags it came with.
public final class ExportPgn implements ExportPgnUseCase {

    private static final DateTimeFormatter PGN_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT);

    private final GameRepository repository;

    public ExportPgn(GameRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String export(GameId gameId) {
        var game = Games.load(repository, gameId);
        var finished = game.status() == GameStatus.FINISHED;
        var result = game.result().map(r -> switch (r) {
            case WHITE_WIN -> PgnGame.Result.WHITE_WINS;
            case BLACK_WIN -> PgnGame.Result.BLACK_WINS;
            case DRAW -> PgnGame.Result.DRAW;
        }).orElse(PgnGame.Result.UNKNOWN);

        var moves = game.moves().stream().map(PlayedMove::move).toList();
        if (game.origin() == GameOrigin.IMPORTED) {
            // Its own tags, as imported (players, event, date…), with the
            // result as Kopi stores it.
            var tags = new LinkedHashMap<>(game.pgnTags());
            tags.put("Result", result.token());
            return Pgn.format(new PgnGame(tags, game.initialPosition(), moves, result));
        }

        var tags = new LinkedHashMap<String, String>();
        tags.put("Event", "Kopi Chess");
        tags.put("Site", "Kopi Chess");
        tags.put("Date", PGN_DATE.format(game.createdAt().atZone(ZoneOffset.UTC)));
        tags.put("Round", "-");
        tags.put("White", player(game, Color.WHITE));
        tags.put("Black", player(game, Color.BLACK));
        tags.put("Result", result.token());
        tags.put("Termination", finished ? "normal" : "unterminated");
        tags.put("PlyCount", String.valueOf(game.moves().size()));
        return Pgn.format(new PgnGame(tags, game.initialPosition(), moves, result));
    }

    /// `Player` for the person, `Kopi Pawn` … `Kopi Queen` for the bot.
    private static String player(Game game, Color side) {
        return game.humanColor().orElseThrow() == side ? "Player" : "Kopi " + botName(game.botProfile().orElseThrow());
    }

    private static String botName(BotProfile profile) {
        var name = profile.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
