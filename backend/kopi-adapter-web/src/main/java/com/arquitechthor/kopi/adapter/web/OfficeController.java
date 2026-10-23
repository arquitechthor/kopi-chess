package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.adapter.web.OfficeMoveInput.Candidate;
import com.arquitechthor.kopi.adapter.web.OfficeMoveInput.Resolution;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.PlayMoveCommand;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameStatus;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// Office mode's move form: the person types a move in algebraic notation
/// (`e4`, `Cf3`, `O-O`) and it is matched against the legal moves (design
/// §11.5, ADR-013). No JavaScript: a plain form, Post/Redirect/Get.
///
/// After the redirect the page says what was understood ("You played Cf3",
/// with the bot's reply) or why not ("did you mean axb5 or Axb5?"), keeping
/// the typed text in the field so it can be corrected.
@Controller
class OfficeController {

    static final String VIEW_COOKIE = "kopi-view";
    static final String OFFICE = "office";

    /// Flash attributes of the result.
    static final String PLAYED = "played";
    static final String REPLY = "reply";
    static final String TYPED = "typed";
    static final String CANDIDATES = "candidates";

    private final GetGameUseCase getGame;
    private final PlayMoveUseCase playMove;
    private final MessageSource messages;

    OfficeController(GetGameUseCase getGame, PlayMoveUseCase playMove, MessageSource messages) {
        this.getGame = getGame;
        this.playMove = playMove;
        this.messages = messages;
    }

    /// The cookie that remembers the view: office mode, or (expired) the board.
    static Cookie viewCookie(boolean office) {
        var cookie = new Cookie(VIEW_COOKIE, office ? OFFICE : "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(office ? (int) Duration.ofDays(365).toSeconds() : 0);
        return cookie;
    }

    @PostMapping("/games/{id}/office-moves")
    String play(@PathVariable UUID id, @RequestParam(defaultValue = "") String move, @RequestParam long version,
            Locale locale, RedirectAttributes redirect) {
        var gameId = new GameId(id);
        var game = getGame.get(gameId);
        var target = "redirect:/games/" + id;
        if (game.status() != GameStatus.IN_PROGRESS) {
            redirect.addFlashAttribute(GameController.ERROR, "GAME_NOT_IN_PROGRESS");
            return target;
        }

        var notation = OfficeNotation.of(messages, locale);
        var candidates = game.legalMoves().stream()
                .map(legal -> new Candidate(legal.uci(), notation.localise(legal.san())))
                .toList();
        switch (OfficeMoveInput.resolve(move, candidates)) {
            case Resolution.Match(var uci, var san) -> {
                try {
                    var after = playMove.play(new PlayMoveCommand(gameId, uci, version));
                    redirect.addFlashAttribute(PLAYED, san);
                    // The bot replied if there are two new moves, not one.
                    if (after.moves().size() > game.moves().size() + 1) {
                        redirect.addFlashAttribute(REPLY, notation.localise(after.moves().getLast().san()));
                    }
                } catch (RuntimeException e) {
                    redirect.addFlashAttribute(GameController.ERROR, GameController.refusal(e));
                    redirect.addFlashAttribute(TYPED, move);
                }
            }
            case Resolution.NoMatch _ -> {
                redirect.addFlashAttribute(GameController.ERROR, "OFFICE_NO_MATCH");
                redirect.addFlashAttribute(TYPED, move);
            }
            case Resolution.Ambiguous(var sans) -> {
                redirect.addFlashAttribute(GameController.ERROR, "OFFICE_AMBIGUOUS");
                redirect.addFlashAttribute(CANDIDATES, String.join(" / ", sans));
                redirect.addFlashAttribute(TYPED, move);
            }
        }
        return target;
    }
}
