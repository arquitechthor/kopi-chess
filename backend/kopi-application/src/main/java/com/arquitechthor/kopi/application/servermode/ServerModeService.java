package com.arquitechthor.kopi.application.servermode;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import com.arquitechthor.kopi.application.port.ServerConnection;
import com.arquitechthor.kopi.application.port.ServerModeStore;
import com.arquitechthor.kopi.application.port.ServerModeStore.Settings;
import java.util.Objects;
import java.util.function.Supplier;

/// [ServerModeUseCase] over the [ServerModeStore], the [KopiAccount] and
/// the [ServerConnection].
///
/// Like the history in the account (ADR-021), it belongs to the account
/// it was turned on with: another account signed in on this computer
/// pauses it, so nobody inherits it by signing in.
public final class ServerModeService implements ServerModeUseCase {

    /// The longest name the relay keeps (`docs/protocolo-rele.md` §10.1).
    public static final int MAX_NAME = 40;

    private final ServerModeStore store;
    private final KopiAccount account;
    private final ServerConnection connection;
    private final Supplier<String> computerName;

    /// @param computerName this computer's own name, for when the person
    ///                     gives none
    public ServerModeService(ServerModeStore store, KopiAccount account, ServerConnection connection,
                             Supplier<String> computerName) {
        this.store = Objects.requireNonNull(store, "store");
        this.account = Objects.requireNonNull(account, "account");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.computerName = Objects.requireNonNull(computerName, "computerName");
    }

    @Override
    public ServerModeView view() {
        var settings = store.settings();
        var session = account.session();
        var other = settings.enabled() && session.isPresent() && !session.get().sub().equals(settings.accountSub());
        var name = settings.name().isEmpty() ? clean(computerName.get()) : settings.name();
        return new ServerModeView(settings.enabled(), session.isPresent(), other, name.isEmpty() ? "Kopi Chess" : name);
    }

    @Override
    public ServerModeView enable(String name) {
        var session = account.session()
                .orElseThrow(() -> new KopiOnlineException(Problem.NOT_SIGNED_IN, "No Kopi session"));
        store.save(new Settings(true, session.sub(), clean(name)));
        apply();
        return view();
    }

    @Override
    public ServerModeView disable() {
        var settings = store.settings();
        store.save(new Settings(false, settings.accountSub(), settings.name()));
        apply();
        return view();
    }

    @Override
    public ServerModeView rename(String name) {
        var settings = store.settings();
        store.save(new Settings(settings.enabled(), settings.accountSub(), clean(name)));
        apply();
        return view();
    }

    @Override
    public void apply() {
        var view = view();
        if (view.active()) {
            connection.serve(view.name());
        } else if (connection.serving()) {
            connection.stopServing();
        }
    }

    /// Without control characters or surrounding spaces, and at most
    /// [#MAX_NAME] characters.
    static String clean(String name) {
        if (name == null) {
            return "";
        }
        var cleaned = name.codePoints().filter(c -> !Character.isISOControl(c))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString().strip();
        return cleaned.length() > MAX_NAME ? cleaned.substring(0, MAX_NAME).strip() : cleaned;
    }
}
