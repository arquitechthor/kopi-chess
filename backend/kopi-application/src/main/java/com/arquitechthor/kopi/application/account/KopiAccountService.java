package com.arquitechthor.kopi.application.account;

import com.arquitechthor.kopi.application.port.KopiAccount;
import com.arquitechthor.kopi.application.port.KopiOnlineException;
import com.arquitechthor.kopi.application.port.KopiOnlineException.Problem;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/// [KopiAccountUseCase] over the [KopiAccount] port. The rules about the
/// id's form are checked here too, so a typo is answered without a trip to
/// Kopi; Kopi checks them again, and decides whether it is free.
public final class KopiAccountService implements KopiAccountUseCase {

    private static final Pattern KOPI_ID = Pattern.compile("[A-Za-z0-9_]{3,20}");

    private final KopiAccount account;

    public KopiAccountService(KopiAccount account) {
        this.account = Objects.requireNonNull(account, "account");
    }

    @Override
    public AccountView account() {
        var session = account.session();
        if (session.isEmpty()) {
            return AccountView.SIGNED_OUT;
        }
        var email = Optional.of(session.get().email()).filter(e -> !e.isBlank());
        try {
            return new AccountView(true, email, account.kopiId(), false, false);
        } catch (KopiOnlineException e) {
            return switch (e.problem()) {
                // The session was revoked or expired at Kopi: it is gone here too.
                case NOT_SIGNED_IN -> AccountView.SIGNED_OUT;
                case PENDING_APPROVAL -> new AccountView(true, email, Optional.empty(), true, false);
                default -> new AccountView(true, email, Optional.empty(), false, true);
            };
        }
    }

    @Override
    public URI beginLogin() {
        return account.beginLogin();
    }

    @Override
    public AccountView completeLogin(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new KopiOnlineException(Problem.LOGIN_FAILED, "Kopi's login page sent no code");
        }
        account.completeLogin(code, state);
        return account();
    }

    @Override
    public AccountView reserveKopiId(String kopiId) {
        var trimmed = kopiId == null ? "" : kopiId.strip();
        if (!KOPI_ID.matcher(trimmed).matches()) {
            throw new KopiOnlineException(Problem.INVALID_KOPI_ID, "Not a Kopi id: " + kopiId);
        }
        account.reserveKopiId(trimmed);
        return account();
    }

    @Override
    public URI signOut() {
        return account.signOut();
    }
}
