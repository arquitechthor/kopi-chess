package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GamePage;
import com.arquitechthor.kopi.application.game.GameSummary;
import com.arquitechthor.kopi.application.game.ListGamesQuery;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.port.GameFilter;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameOrigin;
import com.arquitechthor.kopi.domain.game.GameResult;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/// The history (plan §9): every game, newest first, a page at a time,
/// filtered by origin and by result. A plain `GET` form: the filters are in
/// the address, so a filtered page can be bookmarked or shared.
@Controller
class HistoryController {

    /// Games per page.
    static final int PAGE_SIZE = 20;

    private final ListGamesUseCase listGames;

    HistoryController(ListGamesUseCase listGames) {
        this.listGames = listGames;
    }

    /// One line of the history.
    ///
    /// @param id         the game, for its links
    /// @param createdAt  when it started (or was imported)
    /// @param botProfile the opponent; `null` for an imported game
    /// @param players    an imported game's players, `White – Black` (empty
    ///                   if its PGN names neither); `null` for a game played
    ///                   here
    /// @param humanColor the person's colour; `null` for an imported game
    /// @param moves      full moves played
    /// @param result     the score, `null` while in progress
    /// @param forYou     `WON`, `LOST`, `DRAW` or `IN_PROGRESS` for a game
    ///                   played here (resigning is losing); `null` for an
    ///                   imported one
    /// @param finished   whether it has a review
    record Row(String id, Instant createdAt, BotProfile botProfile, String players, Color humanColor, int moves,
               GameResult result, String forYou, boolean finished) {

        static Row of(GameSummary game) {
            var imported = game.origin() == GameOrigin.IMPORTED;
            return new Row(game.id().toString(), game.createdAt(), game.botProfile().orElse(null),
                    imported ? Players.of(game.pgnTags()) : null, game.humanColor().orElse(null), (game.plies() + 1) / 2,
                    game.result().orElse(null), game.humanColor().map(human -> forYou(game, human)).orElse(null),
                    game.status() == GameStatus.FINISHED);
        }

        private static String forYou(GameSummary game, Color human) {
            return game.result().map(result -> switch (result) {
                case DRAW -> "DRAW";
                case WHITE_WIN -> human == Color.WHITE ? "WON" : "LOST";
                case BLACK_WIN -> human == Color.BLACK ? "WON" : "LOST";
            }).orElse("IN_PROGRESS");
        }
    }

    /// The page as the template shows it.
    ///
    /// @param origin   the origin filter, or `null` for all
    /// @param result   the result filter, or `null` for all
    /// @param previous the previous page's address, `null` on the first
    /// @param next     the next page's address, `null` on the last
    record HistoryPage(List<Row> rows, int page, int totalPages, long total, GameOrigin origin,
                       GameFilter.ResultFilter result, String previous, String next) {

        static HistoryPage of(GamePage games, GameOrigin origin, GameFilter.ResultFilter result) {
            var page = games.page();
            return new HistoryPage(games.games().stream().map(Row::of).toList(), page, games.totalPages(),
                    games.total(), origin, result,
                    page > 0 ? address(page - 1, origin, result) : null,
                    page + 1 < games.totalPages() ? address(page + 1, origin, result) : null);
        }

        /// `/games?page=2&origin=PLAYED`: the filters travel with the page.
        private static String address(int page, GameOrigin origin, GameFilter.ResultFilter result) {
            var uri = UriComponentsBuilder.fromPath("/games").queryParam("page", page);
            if (origin != null) {
                uri.queryParam("origin", origin);
            }
            if (result != null) {
                uri.queryParam("result", result);
            }
            return uri.toUriString();
        }

        /// The filter options, for the form.
        public List<GameOrigin> origins() {
            return List.of(GameOrigin.values());
        }

        public List<GameFilter.ResultFilter> results() {
            return List.of(GameFilter.ResultFilter.values());
        }
    }

    @GetMapping("/games")
    String history(@RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) GameOrigin origin,
            @RequestParam(required = false) GameFilter.ResultFilter result, Model model) {
        var filter = new GameFilter(Optional.ofNullable(origin), Optional.ofNullable(result));
        var games = listGames.list(new ListGamesQuery(Math.max(0, page), PAGE_SIZE, filter));
        model.addAttribute("history", HistoryPage.of(games, origin, result));
        return "history";
    }
}
