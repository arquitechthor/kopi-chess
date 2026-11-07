package com.arquitechthor.kopi.application.history;

/// Keeping the games in the Kopi account and in step between computers
/// (ADR-021). Optional, off by default.
public interface HistoryUseCase {

    /// How it stands now. Never throws.
    HistoryView view();

    /// Turns it on for the account signed in, and syncs once.
    ///
    /// @throws com.arquitechthor.kopi.application.port.KopiOnlineException
    ///         `NOT_SIGNED_IN` without a Kopi session
    HistoryView enable();

    /// Turns it off. What is already in the account stays there.
    HistoryView disable();

    /// Syncs now (if it is on). Problems are shown in the view, not thrown.
    HistoryView syncNow();

    /// Deletes the whole history from the account and turns it off here.
    /// The games on this computer stay.
    ///
    /// @throws com.arquitechthor.kopi.application.port.KopiOnlineException
    ///         if the account cannot be reached
    HistoryView deleteFromAccount();

    /// The background sync: quiet when it is off or paused.
    void sync();
}
