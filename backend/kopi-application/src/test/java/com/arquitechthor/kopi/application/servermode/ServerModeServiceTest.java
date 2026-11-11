package com.arquitechthor.kopi.application.servermode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.ServerConnection;
import com.arquitechthor.kopi.application.port.ServerModeStore;
import com.arquitechthor.kopi.application.port.ServerModeStore.Settings;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// Server mode (ADR-022): off by default, only for the account that turned
/// it on, and the connection follows the switch and the session.
class ServerModeServiceTest {

    static final class MemoryStore implements ServerModeStore {
        Settings settings = Settings.OFF;

        @Override
        public Settings settings() {
            return settings;
        }

        @Override
        public void save(Settings settings) {
            this.settings = settings;
        }
    }

    static final class FakeAccount implements KopiAccount {
        Optional<Session> session = Optional.empty();

        void signIn(String sub) {
            session = Optional.of(new Session(sub, sub + "@example.com"));
        }

        @Override
        public URI beginLogin() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void completeLogin(String code, String state) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Session> session() {
            return session;
        }

        @Override
        public Optional<String> kopiId() {
            return Optional.empty();
        }

        @Override
        public String reserveKopiId(String kopiId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> findKopiId(String kopiId) {
            return Optional.empty();
        }

        @Override
        public URI signOut() {
            session = Optional.empty();
            return URI.create("http://localhost/");
        }
    }

    static final class FakeConnection implements ServerConnection {
        String name;
        int serves;
        int stops;

        @Override
        public void serve(String name) {
            this.name = name;
            serves++;
        }

        @Override
        public void stopServing() {
            name = null;
            stops++;
        }

        @Override
        public boolean serving() {
            return name != null;
        }
    }

    final MemoryStore store = new MemoryStore();
    final FakeAccount account = new FakeAccount();
    final FakeConnection connection = new FakeConnection();
    final ServerModeService service = new ServerModeService(store, account, connection, () -> "DESKTOP-ANA");

    @Test
    void offByDefaultWithThisComputersName() {
        var view = service.view();

        assertThat(view.enabled()).isFalse();
        assertThat(view.active()).isFalse();
        assertThat(view.name()).isEqualTo("DESKTOP-ANA");
        service.apply();
        assertThat(connection.serving()).isFalse();
        assertThat(connection.stops).isZero();
    }

    @Test
    void turningItOnNeedsASession() {
        assertThatThrownBy(() -> service.enable("PC de casa")).isInstanceOf(KopiOnlineException.class)
                .extracting(e -> ((KopiOnlineException) e).problem()).isEqualTo(Problem.NOT_SIGNED_IN);
        assertThat(store.settings).isEqualTo(Settings.OFF);
    }

    @Test
    void turningItOnRemembersTheAccountAndServesUnderItsName() {
        account.signIn("sub-ana");

        var view = service.enable("  PC de casa ");

        assertThat(view.active()).isTrue();
        assertThat(view.name()).isEqualTo("PC de casa");
        assertThat(store.settings).isEqualTo(new Settings(true, "sub-ana", "PC de casa"));
        assertThat(connection.name).isEqualTo("PC de casa");
    }

    @Test
    void aBlankNameIsThisComputersName() {
        account.signIn("sub-ana");

        service.enable("   ");

        assertThat(store.settings.name()).isEmpty();
        assertThat(connection.name).isEqualTo("DESKTOP-ANA");
    }

    @Test
    void namesAreCleanedAndCut() {
        assertThat(ServerModeService.clean("a\u0000b\tc\n")).isEqualTo("abc");
        assertThat(ServerModeService.clean("x".repeat(50))).hasSize(ServerModeService.MAX_NAME);
        assertThat(ServerModeService.clean(null)).isEmpty();
    }

    @Test
    void turningItOffStopsServingAndKeepsTheName() {
        account.signIn("sub-ana");
        service.enable("PC de casa");

        var view = service.disable();

        assertThat(view.enabled()).isFalse();
        assertThat(connection.serving()).isFalse();
        assertThat(store.settings.name()).isEqualTo("PC de casa");
    }

    @Test
    void renamingWhileOnServesUnderTheNewName() {
        account.signIn("sub-ana");
        service.enable("PC de casa");

        service.rename("Portátil");

        assertThat(connection.name).isEqualTo("Portátil");
        assertThat(store.settings.enabled()).isTrue();
    }

    @Test
    void renamingWhileOffDoesNotServe() {
        service.rename("Portátil");

        assertThat(connection.serving()).isFalse();
        assertThat(service.view().name()).isEqualTo("Portátil");
    }

    @Test
    void itIsRememberedAcrossRestarts() {
        account.signIn("sub-ana");
        service.enable("PC de casa");

        var connectionAfterRestart = new FakeConnection();
        new ServerModeService(store, account, connectionAfterRestart, () -> "DESKTOP-ANA").apply();

        assertThat(connectionAfterRestart.name).isEqualTo("PC de casa");
    }

    @Test
    void signingOutStopsServingAndSigningBackInResumes() {
        account.signIn("sub-ana");
        service.enable("PC de casa");

        account.signOut();
        service.apply();
        assertThat(connection.serving()).isFalse();
        assertThat(service.view().enabled()).isTrue();
        assertThat(service.view().active()).isFalse();

        account.signIn("sub-ana");
        service.apply();
        assertThat(connection.serving()).isTrue();
    }

    @Test
    void anotherAccountPausesIt() {
        account.signIn("sub-ana");
        service.enable("PC de casa");

        account.signIn("sub-luis");
        service.apply();

        assertThat(service.view().otherAccount()).isTrue();
        assertThat(service.view().active()).isFalse();
        assertThat(connection.serving()).isFalse();
    }

    @Test
    void turningItOnAgainWithTheOtherAccountMakesItThatAccounts() {
        account.signIn("sub-ana");
        service.enable("PC de casa");
        account.signIn("sub-luis");

        service.enable("PC de casa");

        assertThat(store.settings.accountSub()).isEqualTo("sub-luis");
        assertThat(service.view().active()).isTrue();
    }
}
