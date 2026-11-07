package com.arquitechthor.kopi.application.history;

import com.arquitechthor.kopi.application.game.AnalysisOnFinish;
import com.arquitechthor.kopi.application.port.AccountHistory;
import com.arquitechthor.kopi.application.port.AccountHistory.DeleteResult;
import com.arquitechthor.kopi.application.port.AccountHistory.Entry;
import com.arquitechthor.kopi.application.port.AccountHistory.PutResult;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.application.port.GameRepository;
import com.arquitechthor.kopi.application.port.GameRepository.GameVersion;
import com.arquitechthor.kopi.application.port.HistorySyncStore;
import com.arquitechthor.kopi.application.port.HistorySyncStore.Settings;
import com.arquitechthor.kopi.application.port.HistorySyncStore.SyncedGame;
import com.arquitechthor.kopi.application.port.IdGenerator;
import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.domain.game.Game;
import com.arquitechthor.kopi.domain.game.GameId;
import com.arquitechthor.kopi.domain.game.GameStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// The history in the Kopi account (ADR-021): uploads what changed here,
/// downloads what changed elsewhere, and merges.
///
/// A sync runs in three steps, each one safe to cut short (the next sync
/// carries on):
///
/// 1. **Download** the changes since the last cursor, and apply each one.
/// 2. **Upload deletions:** games known to be in the account that are no
///    longer here.
/// 3. **Upload changes:** games new here or changed since they were last in
///    step (an online game only once it ends: until then it lives in the
///    relay).
///
/// **Merging** a game changed on both sides: games only grow, so if the
/// moves of one are the start of the other's, the longer one wins (with the
/// same moves, the finished one). If they really diverge (the same game in
/// progress played on two computers), both are kept: the account's version
/// comes in as a new game, and this one goes up over it. A deletion wins
/// over an older version, and loses against changes made after it.
///
/// Every game that comes in is rebuilt by replaying its moves
/// ([GameRecord#toGame]): one that does not add up is skipped.
public final class HistorySync implements HistoryUseCase {

    private static final System.Logger log = System.getLogger(HistorySync.class.getName());

    /// The account keeps tombstones this long (protocol §9.3): after a
    /// longer pause, sync as the first time.
    static final Duration TOMBSTONES_KEPT = Duration.ofDays(179);

    /// A synced version that no game has: forces an upload.
    private static final long NEVER = -1;

    private final AccountHistory remote;
    private final HistorySyncStore store;
    private final GameRepository games;
    private final AnalysisJobQueue analyses;
    private final KopiAccount account;
    private final IdGenerator ids;
    private final Clock clock;

    public HistorySync(AccountHistory remote, HistorySyncStore store, GameRepository games, AnalysisJobQueue analyses,
                       KopiAccount account, IdGenerator ids, Clock clock) {
        this.remote = Objects.requireNonNull(remote, "remote");
        this.store = Objects.requireNonNull(store, "store");
        this.games = Objects.requireNonNull(games, "games");
        this.analyses = Objects.requireNonNull(analyses, "analyses");
        this.account = Objects.requireNonNull(account, "account");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized HistoryView view() {
        var settings = store.settings();
        Optional<KopiAccount.Session> session;
        try {
            session = account.session();
        } catch (RuntimeException e) {
            session = Optional.empty();
        }
        var synced = syncedById();
        var pending = (int) games.versions().stream().filter(v -> isPending(v, synced)).count();
        var other = settings.enabled() && session.isPresent() && !session.get().sub().equals(settings.accountSub());
        return new HistoryView(settings.enabled(), session.isPresent(), other, settings.lastSyncAt(),
                settings.lastProblem(), synced.size(), pending);
    }

    @Override
    public synchronized HistoryView enable() {
        var session = account.session()
                .orElseThrow(() -> new KopiOnlineException(Problem.NOT_SIGNED_IN, "Sign in to keep the history"));
        var settings = store.settings();
        if (!session.sub().equals(settings.accountSub())) {
            // Another account (or the first time): start from nothing and
            // merge everything, as on a first sync.
            store.clearGames();
            settings = new Settings(true, session.sub(), "", Optional.empty(), Optional.empty());
        } else {
            settings = new Settings(true, settings.accountSub(), settings.cursor(), settings.lastSyncAt(),
                    Optional.empty());
        }
        store.saveSettings(settings);
        trySync();
        return view();
    }

    @Override
    public synchronized HistoryView disable() {
        var s = store.settings();
        store.saveSettings(new Settings(false, s.accountSub(), s.cursor(), s.lastSyncAt(), Optional.empty()));
        return view();
    }

    @Override
    public synchronized HistoryView syncNow() {
        trySync();
        return view();
    }

    @Override
    public synchronized HistoryView deleteFromAccount() {
        account.session()
                .orElseThrow(() -> new KopiOnlineException(Problem.NOT_SIGNED_IN, "Sign in to delete the history"));
        remote.deleteAll();
        store.clearGames();
        store.saveSettings(Settings.OFF);
        return view();
    }

    @Override
    public synchronized void sync() {
        trySync();
    }

    private void trySync() {
        var settings = store.settings();
        if (!settings.enabled()) {
            return;
        }
        Optional<KopiAccount.Session> session;
        try {
            session = account.session();
        } catch (RuntimeException e) {
            return;
        }
        if (session.isEmpty() || !session.get().sub().equals(settings.accountSub())) {
            return; // signed out, or another account: paused
        }
        try {
            download(settings);
            uploadDeletions();
            uploadChanges();
            store.saveSettings(store.settings().synced(clock.instant()));
        } catch (KopiOnlineException e) {
            log.log(System.Logger.Level.INFO, "History not synced: " + e.problem());
            store.saveSettings(store.settings().failed(e.problem().name()));
        }
    }

    // ── 1. Download ──────────────────────────────────────────────────────

    private void download(Settings settings) {
        var cursor = settings.cursor();
        var tooOld = settings.lastSyncAt().map(at -> at.plus(TOMBSTONES_KEPT).isBefore(clock.instant()))
                .orElse(false);
        if (tooOld) {
            cursor = ""; // tombstones may be gone: read everything again
        }
        AccountHistory.Changes page;
        do {
            page = remote.changes(cursor);
            for (var entry : page.entries()) {
                apply(entry);
            }
            cursor = page.cursor();
            store.saveSettings(store.settings().withCursor(cursor));
        } while (page.more());
    }

    /// Applies one entry of the account to the games here.
    private void apply(Entry entry) {
        var id = entry.gameId();
        var synced = store.find(id);
        if (synced.isPresent() && synced.get().rev() >= entry.rev()) {
            return; // already seen (or uploaded from here)
        }
        var local = games.findById(id);
        var syncedVersion = synced.map(SyncedGame::syncedVersion).orElse(NEVER);

        if (entry.deleted()) {
            if (local.isEmpty()) {
                store.remove(id);
            } else if (local.get().version() == syncedVersion) {
                games.delete(id); // unchanged here since: the deletion wins
                store.remove(id);
            } else {
                // Changed here after it was deleted elsewhere: it comes back.
                store.save(new SyncedGame(id, entry.rev(), syncedVersion));
            }
            return;
        }
        var record = entry.game();
        if (record.isEmpty()) {
            return; // written by a newer Kopi Chess
        }
        Game theirs;
        try {
            theirs = record.get().toGame();
        } catch (IllegalArgumentException e) {
            log.log(System.Logger.Level.WARNING, "Skipping a game from the Kopi account: " + e.getMessage());
            return;
        }

        if (local.isEmpty()) {
            // New here, or deleted here but changed elsewhere afterwards.
            games.insert(theirs);
            store.save(new SyncedGame(id, entry.rev(), theirs.version()));
            AnalysisOnFinish.request(analyses, theirs, clock.instant());
            return;
        }
        var mine = local.get();
        if (mine.version() == syncedVersion) {
            replace(mine, theirs, entry.rev());
            return;
        }
        // Changed on both sides.
        var myMoves = GameRecord.of(mine).uciMoves();
        var theirMoves = record.get().uciMoves();
        if (myMoves.equals(theirMoves)) {
            if (mine.status() == GameStatus.IN_PROGRESS && theirs.status() == GameStatus.FINISHED) {
                replace(mine, theirs, entry.rev());
            } else {
                store.save(new SyncedGame(id, entry.rev(), syncedVersion)); // mine goes up
            }
        } else if (startsWith(myMoves, theirMoves)) {
            store.save(new SyncedGame(id, entry.rev(), syncedVersion)); // mine is longer: it goes up
        } else if (startsWith(theirMoves, myMoves)) {
            replace(mine, theirs, entry.rev());
        } else {
            // Diverged: keep both. Theirs comes in as a new game (uploaded
            // as such), and mine goes up over theirs.
            var copy = withId(record.get(), ids.newGameId()).toGame();
            games.insert(copy);
            AnalysisOnFinish.request(analyses, copy, clock.instant());
            store.save(new SyncedGame(id, entry.rev(), syncedVersion));
        }
    }

    /// Puts `theirs` in place of `mine`, which had not changed here.
    private void replace(Game mine, Game theirs, long rev) {
        try {
            if (startsWith(GameRecord.of(theirs).uciMoves(), GameRecord.of(mine).uciMoves())) {
                games.update(theirs, mine.version()); // the same game, further on
            } else {
                games.delete(mine.id()); // another line: the stored moves go
                games.insert(theirs);
            }
        } catch (ConcurrentGameUpdateException e) {
            return; // changed here meanwhile: the next sync merges it
        }
        store.save(new SyncedGame(theirs.id(), rev, theirs.version()));
        if (mine.status() != GameStatus.FINISHED) {
            AnalysisOnFinish.request(analyses, theirs, clock.instant());
        }
    }

    // ── 2. Upload deletions ──────────────────────────────────────────────

    private void uploadDeletions() {
        var here = new HashMap<GameId, GameVersion>();
        games.versions().forEach(v -> here.put(v.id(), v));
        for (var synced : store.syncedGames()) {
            if (here.containsKey(synced.gameId())) {
                continue;
            }
            var result = remote.delete(synced.gameId(), synced.rev());
            if (result instanceof DeleteResult.Conflict(var current) && !current.deleted()) {
                // Changed elsewhere since: deleting here still wins.
                result = remote.delete(synced.gameId(), current.rev());
            }
            if (result instanceof DeleteResult.Done
                    || result instanceof DeleteResult.Conflict(var current) && current.deleted()) {
                store.remove(synced.gameId());
            }
        }
    }

    // ── 3. Upload changes ────────────────────────────────────────────────

    private void uploadChanges() {
        var synced = syncedById();
        for (var version : games.versions()) {
            if (!isPending(version, synced)) {
                continue;
            }
            var game = games.findById(version.id());
            if (game.isEmpty()) {
                continue; // deleted meanwhile: the next sync uploads the deletion
            }
            upload(game.get(), Optional.ofNullable(synced.get(version.id())), true);
        }
    }

    private void upload(Game game, Optional<SyncedGame> synced, boolean retry) {
        var rev = synced.map(SyncedGame::rev).orElse(0L);
        switch (remote.put(game.id(), rev, GameRecord.of(game))) {
            case PutResult.Stored(var stored) -> store.save(new SyncedGame(game.id(), stored, game.version()));
            case PutResult.Conflict(var current) when current.isEmpty() -> {
                // The account no longer has it (its history was deleted, or
                // the tombstone expired): it goes up as new.
                store.remove(game.id());
                if (retry) {
                    upload(game, Optional.empty(), false);
                }
            }
            case PutResult.Conflict(var current) -> apply(current.get()); // merged; the next sync uploads
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Map<GameId, SyncedGame> syncedById() {
        var byId = new HashMap<GameId, SyncedGame>();
        store.syncedGames().forEach(s -> byId.put(s.gameId(), s));
        return byId;
    }

    private static boolean isPending(GameVersion version, Map<GameId, SyncedGame> synced) {
        if (!version.syncable()) {
            return false;
        }
        var known = synced.get(version.id());
        return known == null || known.syncedVersion() != version.version();
    }

    private static boolean startsWith(List<String> moves, List<String> prefix) {
        return moves.size() >= prefix.size() && moves.subList(0, prefix.size()).equals(prefix);
    }

    private static GameRecord withId(GameRecord record, GameId id) {
        return new GameRecord(id.value().toString(), record.origin(), record.white(), record.black(),
                record.initialFen(), record.moves(), record.termination(), record.result(), record.createdAt(),
                record.finishedAt(), record.pgnTags());
    }
}
