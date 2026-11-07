package com.arquitechthor.kopi.bootstrap;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameMode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.BoundingBox;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/// Plan §10's end-to-end test: what a person does, in a real browser
/// (Chromium, through Playwright), against the whole application on a real
/// port: PostgreSQL in a container and the real Stockfish for the analysis
/// (`KOPI_STOCKFISH`; CI has it).
///
/// New game against `PAWN` → two moves on the board → resign → the review,
/// once the background analysis is done → delete the game.
///
/// Tagged `e2e`: left out of a plain `./mvnw verify` (Playwright downloads
/// its browser the first time); CI runs it (`-Pslow-tests`), and
/// `./mvnw -pl kopi-bootstrap -am verify -Pe2e` runs only this. Each run
/// leaves a Playwright trace in `target/playwright/` (open it with
/// `npx playwright show-trace` or at trace.playwright.dev).
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {AnalysisIT.FAST_NODES, AnalysisIT.FAST_POLL})
@Import(TestcontainersConfiguration.class)
class PlayAndReviewE2EIT {

    private static final Pattern GAME_URL = Pattern.compile(".*/games/([0-9a-f-]{36})$");

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    @Autowired
    GameRepository games;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        // English, whatever the machine's language: the assertions read some texts.
        context = browser.newContext(new Browser.NewContextOptions()
                .setBaseURL("http://localhost:" + port)
                .setLocale("en-GB")
                .setViewportSize(1280, 900));
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        page = context.newPage();
    }

    @AfterEach
    void saveTrace(TestInfo test) {
        context.tracing().stop(new Tracing.StopOptions()
                .setPath(Path.of("target", "playwright", test.getTestMethod().orElseThrow().getName() + ".zip")));
        context.close();
    }

    @Test
    void playResignReviewAndDeleteAGame() {
        // A new game against the weakest bot, with White.
        page.navigate("/");
        page.locator("#new-game input[name=bot][value=PAWN]").check();
        page.locator("#new-game input[name=color][value=WHITE]").check();
        page.locator("#new-game button[type=submit]").click();
        page.waitForURL(GAME_URL);
        var gameId = gameIdOf(page.url());
        assertThat(page.locator("#board cg-board")).isVisible();

        // Two moves, clicked on the board; the bot answers each one.
        play("e2", "e4", 2);
        play("g1", "f3", 4);
        var moves = page.locator(".move-list .move");
        assertThat(moves.nth(0)).hasText("e4");
        assertThat(moves.nth(2)).hasText("♘f3"); // figurine notation on the pages

        // Resigning finishes the game, and queues its analysis.
        page.locator("form[action$='/resign'] button").click();
        var review = page.locator(".panel-actions a[href$='/analysis']");
        assertThat(review).isVisible();

        // The review page follows the analysis and reloads itself when it is done.
        review.click();
        var reviewMoves = page.locator(".review-moves .review-move");
        assertThat(reviewMoves).hasCount(4, new LocatorAssertions.HasCountOptions().setTimeout(60_000));
        for (int i = 0; i < 4; i++) {
            assertThat(reviewMoves.nth(i).locator(".cat")).hasAttribute("class", Pattern.compile("cat cat-\\w+"));
        }
        assertThat(page.locator(".review-summary")).isVisible();

        // Stepping to the first move shows its verdict, and the badge on the board.
        page.locator(".review-nav button[data-go=next]").click();
        assertThat(page.locator(".review-detail[data-ply='1']")).isVisible();
        assertThat(page.locator("#board svg.cg-custom-svgs g")).not().hasCount(0);

        // Deleting it, from the game page, takes the person back to the history.
        page.locator(".panel-actions a.btn", new Page.LocatorOptions().setHasText("Back to the game")).first()
                .click();
        page.locator("summary", new Page.LocatorOptions().setHasText("Delete game")).click();
        page.locator("form[action$='/delete'] button").click();
        page.waitForURL("**/games"); // exactly: no ";jsessionid=…" in the address
        Assertions.assertThat(games.findById(gameId)).isEmpty();
    }

    @Test
    void twoPeoplePlayAMateOnTheTurningBoardAndReviewIt() {
        // A game for two people at this computer (ADR-018).
        page.navigate("/");
        page.locator("#local-game input[name=white]").fill("Ana");
        page.locator("#local-game input[name=black]").fill("Luis");
        page.locator("#local-game button[type=submit]").click();
        page.waitForURL(GAME_URL);
        var gameId = gameIdOf(page.url());
        assertThat(page.locator(".game-status")).hasText("Ana to move");

        // Fool's mate. The board turns after every move, so each one plays
        // from their own side; no bot answers.
        var state = page.locator("#board-state");
        play("f2", "f3", 1);
        assertThat(state).hasAttribute("data-orientation", "black");
        assertThat(page.locator(".game-status")).hasText("Luis to move");
        play("e7", "e5", 2);
        assertThat(state).hasAttribute("data-orientation", "white");
        play("g2", "g4", 3);
        play("d8", "h4", 4);
        assertThat(page.locator(".game-status")).hasText("Checkmate. Black wins.");

        // Reviewed like any other game.
        page.locator(".panel-actions a[href$='/analysis']").click();
        assertThat(page.locator(".review-moves .review-move"))
                .hasCount(4, new LocatorAssertions.HasCountOptions().setTimeout(60_000));
        assertThat(page.locator(".eyebrow").first()).containsText("Ana – Luis");
        Assertions.assertThat(games.findById(gameId).orElseThrow().mode()).isEqualTo(GameMode.LOCAL);
    }

    /// Clicks `from`, then `to`, on the board, as it is turned now, and
    /// waits until the move list has `plies` moves (against a bot, the
    /// person's and the bot's reply).
    private void play(String from, String to, int plies) {
        assertThat(page.locator("#board-state")).hasAttribute("data-movable", "true");
        var whiteAtBottom = "white".equals(page.locator("#board-state").getAttribute("data-orientation"));
        var board = page.locator("#board cg-board").boundingBox();
        click(board, from, whiteAtBottom);
        click(board, to, whiteAtBottom);
        page.waitForFunction("n => [...document.querySelectorAll('.move-list .move')]"
                + ".filter(m => m.textContent.trim()).length === n", plies);
    }

    private void click(BoundingBox board, String square, boolean whiteAtBottom) {
        var file = square.charAt(0) - 'a';
        var rank = square.charAt(1) - '1';
        var column = whiteAtBottom ? file : 7 - file;
        var row = whiteAtBottom ? 7 - rank : rank;
        var size = board.width / 8;
        page.mouse().click(board.x + (column + 0.5) * size, board.y + (row + 0.5) * size);
    }

    private static GameId gameIdOf(String url) {
        var matcher = GAME_URL.matcher(url);
        Assertions.assertThat(matcher.matches()).as("game URL %s", url).isTrue();
        return new GameId(UUID.fromString(matcher.group(1)));
    }
}
