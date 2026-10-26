package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GetBotRecordsUseCase;
import com.arquitechthor.kopi.application.game.ListGamesQuery;
import com.arquitechthor.kopi.application.game.ListGamesUseCase;
import com.arquitechthor.kopi.application.port.BotRecord;
import com.arquitechthor.kopi.domain.game.BotProfile;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/// The landing page: choose a bot (seeing your record against each) and a
/// colour, and see the latest games.
@Controller
class HomeController {

    /// The bots that can be chosen: all five. (The Stockfish ones answer
    /// "unavailable" if the engine is missing, and the form says so.)
    static final Set<BotProfile> AVAILABLE_BOTS = EnumSet.allOf(BotProfile.class);

    /// How many games the "recent games" list shows.
    static final int RECENT_GAMES = 10;

    private final ListGamesUseCase listGames;
    private final GetBotRecordsUseCase botRecords;

    HomeController(ListGamesUseCase listGames, GetBotRecordsUseCase botRecords) {
        this.listGames = listGames;
        this.botRecords = botRecords;
    }

    /// A bot as the form shows it, with the person's record against it.
    record BotChoice(BotProfile profile, boolean available, BotRecord record) {}

    @GetMapping("/")
    String home(Model model) {
        var records = botRecords.records();
        List<BotChoice> bots = Arrays.stream(BotProfile.values())
                .map(profile -> new BotChoice(profile, AVAILABLE_BOTS.contains(profile), records.get(profile)))
                .toList();
        model.addAttribute("bots", bots);
        model.addAttribute("recentGames", listGames.list(new ListGamesQuery(0, RECENT_GAMES)).games());
        return "home";
    }
}
