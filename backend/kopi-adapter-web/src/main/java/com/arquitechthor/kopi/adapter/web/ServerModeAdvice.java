package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.servermode.ServerModeUseCase;
import com.arquitechthor.kopi.application.servermode.ServerModeView;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/// Puts server mode in every page's model, so every page can warn while it
/// is on (ADR-022): the header (and office mode's) shows it.
@ControllerAdvice(basePackageClasses = WebAdapterConfiguration.class)
class ServerModeAdvice {

    private static final System.Logger log = System.getLogger(ServerModeAdvice.class.getName());

    private final ServerModeUseCase serverMode;

    ServerModeAdvice(ServerModeUseCase serverMode) {
        this.serverMode = serverMode;
    }

    /// `null` if it cannot be read just now: the page still shows.
    @ModelAttribute("serverMode")
    ServerModeView serverMode() {
        try {
            return serverMode.view();
        } catch (RuntimeException e) {
            log.log(System.Logger.Level.WARNING, "Server mode could not be read", e);
            return null;
        }
    }
}
