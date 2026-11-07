package com.arquitechthor.kopi.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KopiAccountServiceTest {

    /// A Kopi account in memory: a session or not, an id or not, and an
    /// error to throw when the id is asked for.
    static final class FakeAccount implements KopiAccount {
        Session session;
        String kopiId;
        Problem failure;
        int reservations;

        @Override
        public URI beginLogin() {
            return URI.create("https://login.example/authorize");
        }

        @Override
        public void completeLogin(String code, String state) {
            session = new Session("sub-1", "ana@example.com");
        }

        @Override
        public Optional<Session> session() {
            return Optional.ofNullable(session);
        }

        @Override
        public Optional<String> kopiId() {
            if (failure != null) {
                throw new KopiOnlineException(failure, "fake");
            }
            return Optional.ofNullable(kopiId);
        }

        @Override
        public String reserveKopiId(String id) {
            reservations++;
            kopiId = id;
            return id;
        }

        @Override
        public Optional<String> findKopiId(String id) {
            return Optional.empty();
        }

        @Override
        public URI signOut() {
            session = null;
            return URI.create("https://login.example/logout");
        }
    }

    private final FakeAccount account = new FakeAccount();
    private final KopiAccountService service = new KopiAccountService(account);

    @Test
    void withoutASessionTheAccountIsSignedOut() {
        assertThat(service.account()).isEqualTo(AccountView.SIGNED_OUT);
        assertThat(service.account().readyToPlay()).isFalse();
    }

    @Test
    void afterSigningInItShowsTheEmailAndWhetherThereIsAnId() {
        var view = service.completeLogin("code", "state");

        assertThat(view.signedIn()).isTrue();
        assertThat(view.email()).hasValue("ana@example.com");
        assertThat(view.kopiId()).isEmpty();
        assertThat(view.readyToPlay()).isFalse();
    }

    @Test
    void aCallbackWithoutACodeIsAFailedLogin() {
        assertThatThrownBy(() -> service.completeLogin("", "state"))
                .isInstanceOfSatisfying(KopiOnlineException.class,
                        e -> assertThat(e.problem()).isEqualTo(Problem.LOGIN_FAILED));
    }

    @Test
    void reservingAnIdMakesTheAccountReady() {
        account.session = new KopiAccount.Session("sub-1", "");

        var view = service.reserveKopiId("  Ana_92 ");

        assertThat(view.kopiId()).hasValue("Ana_92");
        assertThat(view.email()).isEmpty();
        assertThat(view.readyToPlay()).isTrue();
    }

    @Test
    void anIdWithTheWrongShapeNeverReachesKopi() {
        account.session = new KopiAccount.Session("sub-1", "");

        assertThatThrownBy(() -> service.reserveKopiId("añá"))
                .isInstanceOfSatisfying(KopiOnlineException.class,
                        e -> assertThat(e.problem()).isEqualTo(Problem.INVALID_KOPI_ID));
        assertThat(account.reservations).isZero();
    }

    @Test
    void anAccountPendingApprovalIsShownAsSuch() {
        account.session = new KopiAccount.Session("sub-1", "ana@example.com");
        account.failure = Problem.PENDING_APPROVAL;

        assertThat(service.account().pendingApproval()).isTrue();
    }

    @Test
    void anUnreachableKopiKeepsTheSession() {
        account.session = new KopiAccount.Session("sub-1", "ana@example.com");
        account.failure = Problem.UNREACHABLE;

        var view = service.account();
        assertThat(view.signedIn()).isTrue();
        assertThat(view.unreachable()).isTrue();
    }

    @Test
    void aRevokedSessionIsSignedOut() {
        account.session = new KopiAccount.Session("sub-1", "ana@example.com");
        account.failure = Problem.NOT_SIGNED_IN;

        assertThat(service.account()).isEqualTo(AccountView.SIGNED_OUT);
    }
}
