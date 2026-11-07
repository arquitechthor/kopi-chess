package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.history.HistoryUseCase;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// The history in the Kopi account (ADR-021), from the account page: turn
/// it on or off, sync now, or delete it from the account. Every action goes
/// back to `/account`, where its state is shown.
@Controller
class AccountHistoryController {

    private final HistoryUseCase history;

    AccountHistoryController(HistoryUseCase history) {
        this.history = history;
    }

    @PostMapping("/account/history/enable")
    String enable(RedirectAttributes redirect) {
        return act(redirect, history::enable);
    }

    @PostMapping("/account/history/disable")
    String disable(RedirectAttributes redirect) {
        return act(redirect, history::disable);
    }

    @PostMapping("/account/history/sync")
    String sync(RedirectAttributes redirect) {
        return act(redirect, history::syncNow);
    }

    @PostMapping("/account/history/delete")
    String delete(RedirectAttributes redirect) {
        var page = act(redirect, history::deleteFromAccount);
        if (!redirect.getFlashAttributes().containsKey(AccountController.ERROR)) {
            redirect.addFlashAttribute("historyDeleted", true);
        }
        return page;
    }

    private static String act(RedirectAttributes redirect, Runnable action) {
        try {
            action.run();
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(AccountController.ERROR, e.problem().name());
        }
        return "redirect:/account";
    }
}
