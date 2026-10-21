package com.arquitechthor.kopi.adapter.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// The landing page. For now it only says what Kopi Chess is and lets the
/// visitor pick a language; choosing a bot arrives in phase 3.
@Controller
class HomeController {

    @GetMapping("/")
    String home() {
        return "home";
    }
}
