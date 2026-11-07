package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.online.OnlineUseCase;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.domain.chess.Color;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// The online page (plan of player versus player, phase D): invite a friend
/// by their Kopi id, answer invitations, and the online games in progress.
/// Opening it brings the games here in step with the relay.
@Controller
class OnlineController {

    private final OnlineUseCase online;
    private final SseOnlineEvents events;

    OnlineController(OnlineUseCase online, SseOnlineEvents events) {
        this.online = online;
        this.events = events;
    }

    @GetMapping("/online")
    String page(Model model) {
        model.addAttribute("lobby", online.lobby());
        return "online";
    }

    /// The page's own refresh: told when an invitation or a game changes.
    @GetMapping("/online/events")
    @ResponseBody
    SseEmitter follow() {
        return events.subscribeLobby();
    }

    /// `color` is `WHITE`, `BLACK` or `RANDOM` (mine).
    @PostMapping("/online/invite")
    String invite(@RequestParam(defaultValue = "") String kopiId, @RequestParam(defaultValue = "RANDOM") String color,
            RedirectAttributes redirect) {
        var mine = switch (color) {
            case "WHITE" -> Optional.of(Color.WHITE);
            case "BLACK" -> Optional.of(Color.BLACK);
            default -> Optional.<Color>empty();
        };
        return act(redirect, () -> {
            online.invite(kopiId, mine);
            redirect.addFlashAttribute("invited", kopiId.strip());
        }, kopiId);
    }

    @PostMapping("/online/invitations/{relayGameId}/accept")
    String accept(@PathVariable String relayGameId, RedirectAttributes redirect) {
        try {
            return "redirect:/games/" + online.accept(relayGameId);
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(AccountController.ERROR, e.problem().name());
            return "redirect:/online";
        }
    }

    @PostMapping("/online/invitations/{relayGameId}/decline")
    String decline(@PathVariable String relayGameId, RedirectAttributes redirect) {
        return act(redirect, () -> online.decline(relayGameId), null);
    }

    @PostMapping("/online/invitations/{relayGameId}/cancel")
    String cancel(@PathVariable String relayGameId, RedirectAttributes redirect) {
        return act(redirect, () -> online.cancel(relayGameId), null);
    }

    @PostMapping("/online/invitations/{relayGameId}/dismiss")
    String dismiss(@PathVariable String relayGameId, RedirectAttributes redirect) {
        return act(redirect, () -> online.dismiss(relayGameId), null);
    }

    private static String act(RedirectAttributes redirect, Runnable action, String kopiId) {
        try {
            action.run();
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(AccountController.ERROR, e.problem().name());
            if (kopiId != null) {
                redirect.addFlashAttribute("kopiId", kopiId);
            }
        }
        return "redirect:/online";
    }
}
