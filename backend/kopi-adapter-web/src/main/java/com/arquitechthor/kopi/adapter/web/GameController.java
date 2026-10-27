package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.CreateGameCommand;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
import com.arquitechthor.kopi.application.game.DeleteGameUseCase;
import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.game.GetGameUseCase;
import com.arquitechthor.kopi.application.game.InvalidUciException;
import com.arquitechthor.kopi.application.game.PlayMoveCommand;
import com.arquitechthor.kopi.application.game.PlayMoveUseCase;
import com.arquitechthor.kopi.application.game.ResignCommand;
import com.arquitechthor.kopi.application.game.ResignUseCase;
import com.arquitechthor.kopi.application.game.VersionMismatchException;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.game.BotProfile;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameNotInProgressException;
import com.arquitechthor.kopi.domain.game.GameRuleException;
import com.arquitechthor.kopi.domain.game.IllegalMoveException;
import com.arquitechthor.kopi.domain.game.NotYourTurnException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// The game pages. Classic forms with Post/Redirect/Get: a move is a `POST`
/// that redirects back to the game, so reloading the page never plays a
/// move twice. The board's script only fills in and submits the move form
/// (design §11.3: no chess rules in JavaScript).
///
/// A refused move or resignation does not show an error page: the game is
/// shown again with a translated message (`error.<CODE>`), as it is.
@Controller
class GameController {

    /// Flash attribute with the code of the last refused action.
    static final String ERROR = "error";

    /// Flash attribute for the history: a game was just deleted.
    static final String DELETED = "deleted";

    private final CreateGameUseCase createGame;
    private final GetGameUseCase getGame;
    private final PlayMoveUseCase playMove;
    private final ResignUseCase resign;
    private final DeleteGameUseCase deleteGame;
    private final MessageSource messages;

    GameController(CreateGameUseCase createGame, GetGameUseCase getGame, PlayMoveUseCase playMove,
            ResignUseCase resign, DeleteGameUseCase deleteGame, MessageSource messages) {
        this.createGame = createGame;
        this.getGame = getGame;
        this.playMove = playMove;
        this.resign = resign;
        this.deleteGame = deleteGame;
        this.messages = messages;
    }

    /// From the home page form. `color` is `WHITE`, `BLACK` or `RANDOM`.
    @PostMapping("/games")
    String create(@RequestParam BotProfile bot, @RequestParam String color, RedirectAttributes redirect) {
        var humanColor = switch (color) {
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            default -> ThreadLocalRandom.current().nextBoolean() ? Color.WHITE : Color.BLACK;
        };
        try {
            var game = createGame.create(new CreateGameCommand(humanColor, bot));
            return "redirect:/games/" + game.id();
        } catch (BotUnavailableException _) {
            redirect.addFlashAttribute(ERROR, "BOT_UNAVAILABLE");
            return "redirect:/";
        }
    }

    /// The game, on the board or in office mode (design §11.5).
    ///
    /// `?view=office` switches to office mode and `?view=board` back; the
    /// choice is remembered in a cookie, like the language.
    @GetMapping("/games/{id}")
    String game(@PathVariable UUID id, @RequestParam(required = false) String view,
            @CookieValue(name = OfficeController.VIEW_COOKIE, required = false) String remembered,
            HttpServletResponse response, Locale locale, Model model) {
        var office = view == null ? OfficeController.OFFICE.equals(remembered) : OfficeController.OFFICE.equals(view);
        if (view != null) {
            response.addCookie(OfficeController.viewCookie(office));
        }
        var game = getGame.get(new GameId(id));
        if (office) {
            model.addAttribute("office", OfficePage.of(game, OfficeNotation.of(messages, locale)));
            return "game-office";
        }
        model.addAttribute("game", GamePage.of(game));
        return "game";
    }

    @PostMapping("/games/{id}/moves")
    String play(@PathVariable UUID id, @RequestParam String uci, @RequestParam long version,
            RedirectAttributes redirect) {
        try {
            playMove.play(new PlayMoveCommand(new GameId(id), uci, version));
        } catch (RuntimeException e) {
            redirect.addFlashAttribute(ERROR, refusal(e));
        }
        return "redirect:/games/" + id;
    }

    /// The same move as [#play], for `board.js`: instead of a redirect, the
    /// answer is just the part of the page that changes (the `live`
    /// fragment of `game.html`), so the page is not reloaded and the board
    /// can animate the bot's reply. A refused move answers the fragment too,
    /// with the message and the position as it was.
    @PostMapping("/games/{id}/live-moves")
    String playLive(@PathVariable UUID id, @RequestParam String uci, @RequestParam long version, Model model) {
        var gameId = new GameId(id);
        try {
            model.addAttribute("game", GamePage.of(playMove.play(new PlayMoveCommand(gameId, uci, version))));
        } catch (RuntimeException e) {
            model.addAttribute(ERROR, refusal(e));
            model.addAttribute("game", GamePage.of(getGame.get(gameId)));
        }
        return "game :: live";
    }

    @PostMapping("/games/{id}/resign")
    String resign(@PathVariable UUID id, @RequestParam long version, RedirectAttributes redirect) {
        try {
            resign.resign(new ResignCommand(new GameId(id), version));
        } catch (RuntimeException e) {
            redirect.addFlashAttribute(ERROR, refusal(e));
        }
        return "redirect:/games/" + id;
    }

    /// From the confirmation on the game's pages; then the history says so.
    /// A game already gone (the form sent twice, another tab) ends the same
    /// way: what was asked for is done.
    @PostMapping("/games/{id}/delete")
    String delete(@PathVariable UUID id, RedirectAttributes redirect) {
        try {
            deleteGame.delete(new GameId(id));
        } catch (GameNotFoundException _) {
            // already deleted
        }
        redirect.addFlashAttribute(DELETED, true);
        return "redirect:/games";
    }

    /// The code of a refused action, or the exception again if it is not a
    /// refusal but a failure (a bug, the database down): those deserve the
    /// error page, not a polite message.
    static String refusal(RuntimeException e) {
        return switch (e) {
            case GameRuleException rule -> switch (rule) {
                case IllegalMoveException _ -> "ILLEGAL_MOVE";
                case NotYourTurnException _ -> "NOT_YOUR_TURN";
                case GameNotInProgressException _ -> "GAME_NOT_IN_PROGRESS";
            };
            case InvalidUciException _ -> "INVALID_UCI";
            case VersionMismatchException _, ConcurrentGameUpdateException _ -> "VERSION_MISMATCH";
            case BotUnavailableException _ -> "BOT_UNAVAILABLE";
            default -> throw e;
        };
    }
}
