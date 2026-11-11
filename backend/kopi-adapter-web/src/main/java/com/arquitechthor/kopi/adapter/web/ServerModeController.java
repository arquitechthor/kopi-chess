package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.servermode.ServerModeUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/// Server mode (ADR-022), from the account page: turn it on or off, or
/// change the name the phone shows. Every action goes back to the account
/// page, where its state is shown.
@Controller
class ServerModeController {

    private static final String BACK = "redirect:/account#server-mode";

    private final ServerModeUseCase serverMode;

    ServerModeController(ServerModeUseCase serverMode) {
        this.serverMode = serverMode;
    }

    @PostMapping("/account/server-mode/enable")
    String enable(@RequestParam(defaultValue = "") String name, RedirectAttributes redirect) {
        try {
            serverMode.enable(name);
        } catch (KopiOnlineException e) {
            redirect.addFlashAttribute(AccountController.ERROR, e.problem().name());
        }
        return BACK;
    }

    @PostMapping("/account/server-mode/disable")
    String disable() {
        serverMode.disable();
        return BACK;
    }

    @PostMapping("/account/server-mode/name")
    String rename(@RequestParam(defaultValue = "") String name) {
        serverMode.rename(name);
        return BACK;
    }
}
