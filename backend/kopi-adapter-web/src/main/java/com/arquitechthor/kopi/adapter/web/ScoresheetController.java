package com.arquitechthor.kopi.adapter.web;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/// "Scoresheet": a blank sheet to write down a game played over the board,
/// on screen or printed. The game's details (event, date, players…) at the
/// top and the numbered moves below, a box for White's and one for Black's.
///
/// Nothing is stored, on the server or in the browser: the sheet is blank
/// every time it opens.
@Controller
class ScoresheetController {

    /// The numbered moves on one sheet: two columns, as on paper.
    static final int MOVES = 60;

    @GetMapping("/scoresheet")
    String scoresheet(Model model) {
        int half = MOVES / 2;
        model.addAttribute("columns", List.of(
                IntStream.rangeClosed(1, half).boxed().toList(),
                IntStream.rangeClosed(half + 1, MOVES).boxed().toList()));
        return "scoresheet";
    }
}
