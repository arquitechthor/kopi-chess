package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.CreateGameCommand;
import com.arquitechthor.kopi.application.game.CreateGameUseCase;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    private final CreateGameUseCase createGame;
    private final GetGameUseCase getGame;
    private final PlayMoveUseCase playMove;
    private final ResignUseCase resign;

    GameController(CreateGameUseCase createGame, GetGameUseCase getGame, PlayMoveUseCase playMove,
            ResignUseCase resign) {
        this.createGame = createGame;
        this.getGame = getGame;
        this.playMove = playMove;
        this.resign = resign;
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

    @GetMapping("/games/{id}")
    String game(@PathVariable UUID id, Model model) {
        model.addAttribute("game", GamePage.of(getGame.get(new GameId(id))));
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

    @PostMapping("/games/{id}/resign")
    String resign(@PathVariable UUID id, @RequestParam long version, RedirectAttributes redirect) {
        try {
            resign.resign(new ResignCommand(new GameId(id), version));
        } catch (RuntimeException e) {
            redirect.addFlashAttribute(ERROR, refusal(e));
        }
        return "redirect:/games/" + id;
    }

    /// The code of a refused action, or the exception again if it is not a
    /// refusal but a failure (a bug, the database down): those deserve the
    /// error page, not a polite message.
    private static String refusal(RuntimeException e) {
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
