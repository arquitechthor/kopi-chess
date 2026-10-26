package com.arquitechthor.kopi.application.port;

import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/// The person's record against one bot: how the games against it stand,
/// from the person's side (plan §9, asked for on 2026-10-26).
///
/// @param won        games the person won
/// @param lost       games the person lost, resignations included
/// @param drawn      drawn games
/// @param unfinished games still in progress, which can be resumed
public record BotRecord(int won, int lost, int drawn, int unfinished) {

    /// No games yet.
    public static final BotRecord NONE = new BotRecord(0, 0, 0, 0);

    public BotRecord {
        if (won < 0 || lost < 0 || drawn < 0 || unfinished < 0) {
            throw new IllegalArgumentException("Counts cannot be negative");
        }
    }

    /// Every game against the bot.
    public int played() {
        return won + lost + drawn + unfinished;
    }

    /// The records of these games, one for each of the five bots (`NONE`
    /// for a bot never played). Imported games do not count: they have no
    /// bot. This is what the numbers mean; the database adapter must give
    /// the same in SQL.
    public static Map<BotProfile, BotRecord> tally(Stream<Game> games) {
        var records = new EnumMap<BotProfile, BotRecord>(BotProfile.class);
        for (var profile : BotProfile.values()) {
            records.put(profile, NONE);
        }
        games.filter(game -> game.origin() == GameOrigin.PLAYED).forEach(game -> {
            var profile = game.botProfile().orElseThrow();
            records.put(profile, records.get(profile).with(game));
        });
        return Collections.unmodifiableMap(records);
    }

    /// This record with one more game.
    private BotRecord with(Game game) {
        if (game.status() == GameStatus.IN_PROGRESS) {
            return new BotRecord(won, lost, drawn, unfinished + 1);
        }
        var human = game.humanColor().orElseThrow();
        return switch (game.result().orElseThrow()) {
            case DRAW -> new BotRecord(won, lost, drawn + 1, unfinished);
            case WHITE_WIN -> human == Color.WHITE ? new BotRecord(won + 1, lost, drawn, unfinished)
                    : new BotRecord(won, lost + 1, drawn, unfinished);
            case BLACK_WIN -> human == Color.BLACK ? new BotRecord(won + 1, lost, drawn, unfinished)
                    : new BotRecord(won, lost + 1, drawn, unfinished);
        };
    }
}
