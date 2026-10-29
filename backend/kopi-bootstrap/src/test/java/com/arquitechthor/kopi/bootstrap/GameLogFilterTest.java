package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.AnalysisJob;
import com.arquitechthor.kopi.application.port.AnalysisJobQueue;
import com.arquitechthor.kopi.domain.game.GameId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/// The game a log line is about (design §14): from the path of a request,
/// and from the job an analysis worker claims.
class GameLogFilterTest {

    private static final String ID = "5f0c6a1e-8d2b-4c3a-9e7f-1a2b3c4d5e6f";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Nested
    class Requests {

        private final GameLogFilter filter = new GameLogFilter();

        @Test
        void findsTheGameInThePagesAndInTheApi() {
            assertThat(GameLogFilter.gameId("/games/" + ID)).hasValue(ID);
            assertThat(GameLogFilter.gameId("/games/" + ID + "/analysis")).hasValue(ID);
            assertThat(GameLogFilter.gameId("/api/v1/games/" + ID + "/moves")).hasValue(ID);
            assertThat(GameLogFilter.gameId("/api/v1/games/" + ID.toUpperCase())).hasValue(ID);
        }

        @Test
        void otherPathsAreAboutNoGame() {
            assertThat(GameLogFilter.gameId("/")).isEmpty();
            assertThat(GameLogFilter.gameId("/games")).isEmpty();
            assertThat(GameLogFilter.gameId("/games/import")).isEmpty();
            assertThat(GameLogFilter.gameId("/api/v1/bots")).isEmpty();
            assertThat(GameLogFilter.gameId("/games/not-a-uuid\nforged log line")).isEmpty();
            assertThat(GameLogFilter.gameId("/actuator/games/" + ID)).isEmpty();
        }

        @Test
        void theGameIsSetWhileTheRequestIsHandledAndRemovedAfter() throws Exception {
            var seen = new ArrayList<String>();

            filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/games/" + ID + "/moves"),
                    new MockHttpServletResponse(), (_, _) -> seen.add(MDC.get(GameLogFilter.GAME_ID)));

            assertThat(seen).containsExactly(ID);
            assertThat(MDC.get(GameLogFilter.GAME_ID)).isNull();
        }

        @Test
        void itIsRemovedEvenIfTheRequestFails() {
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/games/" + ID),
                    new MockHttpServletResponse(), (_, _) -> {
                        throw new IllegalStateException("boom");
                    })).isInstanceOf(IllegalStateException.class);

            assertThat(MDC.get(GameLogFilter.GAME_ID)).isNull();
        }
    }

    @Nested
    class AnalysisJobs {

        private final List<AnalysisJob> queued = new ArrayList<>();
        private final AnalysisJobQueue queue = new LoggedAnalysisJobQueue(new AnalysisJobQueue() {
            @Override
            public AnalysisJob enqueue(GameId gameId, Instant now) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<AnalysisJob> claimNext(Instant now) {
                return queued.isEmpty() ? Optional.empty() : Optional.of(queued.removeFirst());
            }

            @Override
            public Optional<AnalysisJob> findByGameId(GameId gameId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int countQueued() {
                return queued.size();
            }

            @Override
            public void complete(long jobId) {}

            @Override
            public void retry(long jobId, String error) {}

            @Override
            public void fail(long jobId, String error) {}

            @Override
            public int recoverStale(Instant lockedBefore, int maxAttempts) {
                throw new UnsupportedOperationException();
            }
        });

        @Test
        void claimingAJobTagsTheThreadWithItsGame() {
            var gameId = new GameId(UUID.fromString(ID));
            queued.add(new AnalysisJob(1, gameId, AnalysisJob.State.RUNNING, 1, Optional.empty(), Instant.EPOCH));

            assertThat(queue.claimNext(Instant.EPOCH)).map(AnalysisJob::gameId).hasValue(gameId);

            assertThat(MDC.get(GameLogFilter.GAME_ID)).isEqualTo(ID);
        }

        @Test
        void noJobNoGame() {
            assertThat(queue.claimNext(Instant.EPOCH)).isEmpty();

            assertThat(MDC.get(GameLogFilter.GAME_ID)).isNull();
        }
    }
}
