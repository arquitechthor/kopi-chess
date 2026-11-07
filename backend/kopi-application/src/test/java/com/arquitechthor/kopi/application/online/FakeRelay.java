package com.arquitechthor.kopi.application.online;

import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.PvpRelay;
import com.arquitechthor.kopi.application.port.PvpRelay.RelayGame;
import com.arquitechthor.kopi.application.port.PvpRelay.RelayMove;
import com.arquitechthor.kopi.application.port.PvpRelay.Status;
import com.arquitechthor.kopi.domain.chess.Color;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// The relay of `docs/protocolo-rele.md`, in memory: the same rules as the
/// Lambdas (who may do what, the next ply only, acknowledgements, the
/// 30-day expiry) and no rules of chess. Each account sees it through
/// [#forAccount].
final class FakeRelay {

    static final class Stored {
        final String id = UUID.randomUUID().toString();
        String whiteSub;
        String blackSub;
        Status status = Status.INVITED;
        Color invitedBy;
        String initialFen;
        Color firstMover = Color.WHITE;
        final List<RelayMove> moves = new ArrayList<>();
        Color drawOfferBy;
        String result;
        String termination;
        Color finishedBy;
        boolean ackWhite;
        boolean ackBlack;
        Instant createdAt;
        Instant lastMoveAt;
        Instant expiresAt;
    }

    private final Map<String, String> kopiIds = new LinkedHashMap<>();
    final Map<String, Stored> games = new LinkedHashMap<>();
    private final Clock clock;

    FakeRelay(Clock clock) {
        this.clock = clock;
    }

    void register(String sub, String kopiId) {
        kopiIds.put(sub, kopiId);
    }

    /// A game the relay still has (not deleted nor expired).
    Optional<Stored> live(String id) {
        var game = games.get(id);
        return game == null || !game.expiresAt.isAfter(clock.instant()) ? Optional.empty() : Optional.of(game);
    }

    private String subOf(String kopiId) {
        return kopiIds.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(kopiId)).map(Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new KopiOnlineException(Problem.KOPI_ID_NOT_FOUND, kopiId));
    }

    PvpRelay forAccount(String sub) {
        return new Facade(sub);
    }

    private final class Facade implements PvpRelay {
        private final String sub;

        Facade(String sub) {
            this.sub = sub;
        }

        private Color colorOf(Stored game) {
            return sub.equals(game.whiteSub) ? Color.WHITE : Color.BLACK;
        }

        private Stored mine(String id) {
            return live(id).filter(game -> sub.equals(game.whiteSub) || sub.equals(game.blackSub))
                    .orElseThrow(() -> new KopiOnlineException(Problem.GAME_NOT_FOUND, id));
        }

        private RelayGame view(Stored game, int fromPly) {
            return new RelayGame(game.id, game.status, kopiIds.get(game.whiteSub), kopiIds.get(game.blackSub),
                    colorOf(game), game.invitedBy, Optional.ofNullable(game.initialFen), game.firstMover,
                    game.moves.size(), Optional.ofNullable(game.lastMoveAt), Optional.ofNullable(game.drawOfferBy),
                    Optional.ofNullable(game.result), Optional.ofNullable(game.termination),
                    Optional.ofNullable(game.finishedBy), game.createdAt, game.expiresAt,
                    game.moves.stream().filter(m -> m.ply() >= fromPly).toList());
        }

        private void require(Stored game, Status status) {
            if (game.status != status) {
                throw new KopiOnlineException(Problem.WRONG_STATUS, game.status.name());
            }
        }

        private void finish(Stored game, String result, String termination) {
            game.status = Status.FINISHED;
            game.result = result;
            game.termination = termination;
            game.finishedBy = colorOf(game);
            game.drawOfferBy = null;
            ack(game, colorOf(game));
        }

        private void ack(Stored game, Color color) {
            if (color == Color.WHITE) {
                game.ackWhite = true;
            } else {
                game.ackBlack = true;
            }
        }

        @Override
        public RelayGame invite(String opponentKopiId, Color color, Optional<String> initialFen) {
            var opponent = subOf(opponentKopiId);
            if (opponent.equals(sub)) {
                throw new KopiOnlineException(Problem.CANNOT_INVITE_YOURSELF, "");
            }
            var game = new Stored();
            var mine = color == null ? Color.WHITE : color;
            game.whiteSub = mine == Color.WHITE ? sub : opponent;
            game.blackSub = mine == Color.WHITE ? opponent : sub;
            game.invitedBy = mine;
            game.initialFen = initialFen.orElse(null);
            game.firstMover = game.initialFen != null && game.initialFen.split(" ")[1].equals("b")
                    ? Color.BLACK : Color.WHITE;
            game.createdAt = clock.instant();
            game.expiresAt = clock.instant().plus(Duration.ofDays(30));
            games.put(game.id, game);
            return view(game, 1);
        }

        @Override
        public List<RelayGame> myGames() {
            return games.values().stream()
                    .filter(game -> sub.equals(game.whiteSub) || sub.equals(game.blackSub))
                    .filter(game -> game.expiresAt.isAfter(clock.instant()))
                    .map(game -> view(game, Integer.MAX_VALUE)).toList();
        }

        @Override
        public RelayGame game(String relayGameId, int fromPly) {
            return view(mine(relayGameId), fromPly);
        }

        @Override
        public RelayGame accept(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.INVITED);
            if (game.invitedBy == colorOf(game)) {
                throw new KopiOnlineException(Problem.NOT_ALLOWED, "");
            }
            game.status = Status.ACTIVE;
            game.expiresAt = clock.instant().plus(Duration.ofDays(30));
            return view(game, 1);
        }

        @Override
        public RelayGame decline(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.INVITED);
            game.status = Status.DECLINED;
            ack(game, colorOf(game));
            return view(game, 1);
        }

        @Override
        public void cancel(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.INVITED);
            games.remove(game.id);
        }

        @Override
        public RelayGame move(String relayGameId, int ply, String uci, String zobrist, Optional<Ending> ending) {
            var game = mine(relayGameId);
            require(game, Status.ACTIVE);
            var toMove = game.moves.size() % 2 == 0 ? game.firstMover : game.firstMover.opposite();
            if (toMove != colorOf(game)) {
                throw new KopiOnlineException(Problem.NOT_YOUR_TURN, "");
            }
            if (ply != game.moves.size() + 1) {
                throw new KopiOnlineException(Problem.WRONG_PLY, "expected " + (game.moves.size() + 1));
            }
            game.moves.add(new RelayMove(ply, uci, zobrist, clock.instant()));
            game.lastMoveAt = clock.instant();
            game.expiresAt = clock.instant().plus(Duration.ofDays(30));
            if (game.drawOfferBy == colorOf(game).opposite()) {
                game.drawOfferBy = null;
            }
            ending.ifPresent(end -> finish(game, end.result(), end.termination()));
            return view(game, Integer.MAX_VALUE);
        }

        @Override
        public RelayGame resign(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.ACTIVE);
            finish(game, colorOf(game) == Color.WHITE ? "0-1" : "1-0", "RESIGNATION");
            return view(game, Integer.MAX_VALUE);
        }

        @Override
        public RelayGame offerDraw(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.ACTIVE);
            if (game.drawOfferBy == colorOf(game).opposite()) {
                return acceptDraw(relayGameId);
            }
            game.drawOfferBy = colorOf(game);
            return view(game, Integer.MAX_VALUE);
        }

        @Override
        public RelayGame acceptDraw(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.ACTIVE);
            if (game.drawOfferBy != colorOf(game).opposite()) {
                throw new KopiOnlineException(Problem.NO_DRAW_OFFER, "");
            }
            finish(game, "1/2-1/2", "DRAW_AGREEMENT");
            return view(game, Integer.MAX_VALUE);
        }

        @Override
        public RelayGame declineDraw(String relayGameId) {
            var game = mine(relayGameId);
            require(game, Status.ACTIVE);
            if (game.drawOfferBy != colorOf(game).opposite()) {
                throw new KopiOnlineException(Problem.NO_DRAW_OFFER, "");
            }
            game.drawOfferBy = null;
            return view(game, Integer.MAX_VALUE);
        }

        @Override
        public void acknowledge(String relayGameId) {
            var game = live(relayGameId).orElse(null);
            if (game == null) {
                return; // already gone: done
            }
            if (game.status != Status.FINISHED && game.status != Status.DECLINED) {
                throw new KopiOnlineException(Problem.WRONG_STATUS, game.status.name());
            }
            var mine = colorOf(game);
            var otherAcked = mine == Color.WHITE ? game.ackBlack : game.ackWhite;
            if (otherAcked) {
                games.remove(game.id);
            } else {
                ack(game, mine);
            }
        }
    }
}
