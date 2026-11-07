package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.account.KopiAccountUseCase;
import com.arquitechthor.kopi.application.history.HistoryUseCase;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// The Kopi account page (ADR-019): sign in on Kopi's login page, reserve
/// the Kopi id, sign out. Needed to play online; never to play here.
///
/// Signing in leaves Kopi Chess for Kopi's own login page, which sends the
/// browser back to `/login/oauth2/code/kopi` (the address registered in the
/// `kopi-chess-client` app client). The password never goes through here.
@Controller
class AccountController {

    /// Flash attribute with the code of a refused action (`account.error.<CODE>`).
    static final String ERROR = "accountError";

    private final KopiAccountUseCase account;
    private final HistoryUseCase history;

    AccountController(KopiAccountUseCase account, HistoryUseCase history) {
        this.account = account;
        this.history = history;
    }

    @GetMapping("/account")
    String page(Model model) {
        model.addAttribute("account", account.account());
        model.addAttribute("accountHistory", history.view());
        return "account";
    }

    /// To Kopi's login page. A `POST`, so another site cannot start it with a link.
    @PostMapping("/account/login")
    String login(RedirectAttributes redirect) {
        try {
            return "redirect:" + account.beginLogin();
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(ERROR, e.problem().name());
            return "redirect:/account";
        }
    }

    /// Where Kopi's login page sends the browser back, with a code (or an
    /// error, if the person cancelled).
    @GetMapping("/login/oauth2/code/kopi")
    String callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            RedirectAttributes redirect) {
        try {
            account.completeLogin(code, state);
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(ERROR, e.problem().name());
        }
        return "redirect:/account";
    }

    @PostMapping("/account/kopi-id")
    String reserve(@RequestParam(defaultValue = "") String kopiId, RedirectAttributes redirect) {
        try {
            account.reserveKopiId(kopiId);
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(ERROR, e.problem().name());
            redirect.addFlashAttribute("kopiId", kopiId);
        }
        return "redirect:/account";
    }

    /// Forgets the session here, then ends the session of Kopi's login page,
    /// which sends the browser back home.
    @PostMapping("/account/logout")
    String logout() {
        return "redirect:" + account.signOut();
    }
}
