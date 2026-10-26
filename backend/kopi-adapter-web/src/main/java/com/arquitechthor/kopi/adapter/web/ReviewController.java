package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.analysis.AnalysisNotPossibleException;
import com.arquitechthor.kopi.application.analysis.GetAnalysisUseCase;
import com.arquitechthor.kopi.application.analysis.RequestAnalysisUseCase;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/// The review of a finished game (plan §8.5): its analysis, or how the
/// analysis is going.
///
/// The analysis runs in the background (ADR-007). While it does, the page
/// follows it live through the API's Server-Sent Events (`review.js`) and
/// reloads itself when it is done; without JavaScript it refreshes every
/// few seconds instead.
@Controller
class ReviewController {

    private final GetGameUseCase getGame;
    private final GetAnalysisUseCase getAnalysis;
    private final RequestAnalysisUseCase requestAnalysis;

    ReviewController(GetGameUseCase getGame, GetAnalysisUseCase getAnalysis, RequestAnalysisUseCase requestAnalysis) {
        this.getGame = getGame;
        this.getAnalysis = getAnalysis;
        this.requestAnalysis = requestAnalysis;
    }

    /// A game still being played goes back to its board. A finished one
    /// whose analysis was never asked for (it finished before analyses
    /// existed) gets it asked for now: the review is where people expect
    /// it to start.
    @GetMapping("/games/{id}/analysis")
    String review(@PathVariable UUID id, Model model) {
        var gameId = new GameId(id);
        var game = getGame.get(gameId);
        if (game.status() != GameStatus.FINISHED) {
            return "redirect:/games/" + id;
        }
        var view = getAnalysis.get(gameId);
        if (view.job().isEmpty() && view.analysis().isEmpty()) {
            try {
                requestAnalysis.request(gameId);
            } catch (AnalysisNotPossibleException _) {
                // Finished before any move: nothing to review.
                model.addAttribute("game", GamePage.of(game));
                return "review-empty";
            }
            view = getAnalysis.get(gameId);
        }
        model.addAttribute("review", ReviewPage.of(game, view));
        return "review";
    }

    /// "Try again" after a failure: queues it anew.
    @PostMapping("/games/{id}/analysis")
    String retry(@PathVariable UUID id) {
        requestAnalysis.request(new GameId(id));
        return "redirect:/games/" + id + "/analysis";
    }
}
