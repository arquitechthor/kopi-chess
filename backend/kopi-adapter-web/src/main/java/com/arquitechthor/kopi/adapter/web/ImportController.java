package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.ImportPgnUseCase;
import com.arquitechthor.kopi.application.game.PgnTooLargeException;
import com.arquitechthor.kopi.domain.chess.Color;
import com.arquitechthor.kopi.domain.chess.InvalidPgnException;
import com.arquitechthor.kopi.domain.game.InvalidImportException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/// "Import PGN" (plan §9): paste a game or choose a `.pgn` file, and see
/// its review.
///
/// A refused PGN shows the form again with the text as it was, the reason
/// in the visitor's language and, when the problem is on one line, that
/// line quoted: «Línea 4, jugada 3… Nxe5: no es una jugada legal en esa
/// posición.» (ADR-011: the domain says what and where, this adapter says
/// it in words).
@Controller
class ImportController {

    private final ImportPgnUseCase importPgn;

    ImportController(ImportPgnUseCase importPgn) {
        this.importPgn = importPgn;
    }

    @GetMapping("/games/import")
    String form(Model model) {
        model.addAttribute("pgn", "");
        return "import";
    }

    /// A chosen file wins over the text box. On success, straight to the
    /// review, which follows the analysis as it runs.
    @PostMapping("/games/import")
    String importGame(@RequestParam(required = false) String pgn,
            @RequestParam(required = false) MultipartFile file, Model model) {
        var text = pgn == null ? "" : pgn;
        if (file != null && !file.isEmpty()) {
            try {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return refused(model, text, ImportError.of("UNREADABLE_FILE"));
            }
        }
        if (text.isBlank()) {
            return refused(model, text, ImportError.of("EMPTY_FORM"));
        }
        try {
            var game = importPgn.importPgn(text);
            return "redirect:/games/" + game.id() + "/analysis";
        } catch (InvalidPgnException e) {
            return refused(model, text, ImportError.of(e, text));
        } catch (InvalidImportException e) {
            return refused(model, text, ImportError.of(e.problem().name()));
        } catch (PgnTooLargeException e) {
            return refused(model, text.length() > 2_000 ? "" : text,
                    new ImportError("PGN_TOO_LARGE", 0, 0, null, null, null, e.limitBytes() / 1024));
        }
    }

    private static String refused(Model model, String text, ImportError error) {
        model.addAttribute("pgn", text);
        model.addAttribute("importError", error);
        return "import";
    }

    /// Why a PGN was refused, ready for the template.
    ///
    /// @param code       `import.error.<code>` is the sentence: a PGN
    ///                   problem (`ILLEGAL_MOVE`…), an import problem
    ///                   (`NOT_FINISHED`…) or a form problem (`EMPTY_FORM`)
    /// @param line       the line, from 1; 0 when it is about no line
    /// @param moveNumber the move number, 0 when it is about no move
    /// @param dots       `.` for White's move, `…` for Black's
    /// @param text       the offending text (`Nxe5`)
    /// @param quote      the whole line, to show it
    /// @param limitKb    for `PGN_TOO_LARGE`, the limit
    record ImportError(String code, int line, int moveNumber, String dots, String text, String quote,
                       int limitKb) {

        static ImportError of(String code) {
            return new ImportError(code, 0, 0, null, null, null, 0);
        }

        static ImportError of(InvalidPgnException e, String pgn) {
            var lines = pgn.lines().toList();
            var quote = e.line() >= 1 && e.line() <= lines.size() ? lines.get(e.line() - 1).strip() : null;
            return new ImportError(e.problem().name(), e.line(), e.moveNumber().orElse(0),
                    e.side().map(side -> side == Color.WHITE ? "." : "…").orElse(null), e.text(), quote, 0);
        }

        /// Whether it points at a move (and not just at a line).
        public boolean atMove() {
            return dots != null;
        }
    }
}
