package com.arquitechthor.kopi.application.port;

/// The relay's notifications (ADR-020, `docs/protocolo-rele.md`, §7): "there
/// is news in one of your games". They carry no data, so a lost one breaks
/// nothing; the listener syncs.
public interface RelayNotifications {

    /// Starts listening (while there is a Kopi session) and calls
    /// `onNews` for every notification and after every reconnection.
    void listen(Runnable onNews);

    /// Stops listening.
    void stop();

    /// Never notifies.
    RelayNotifications NONE = new RelayNotifications() {
        @Override
        public void listen(Runnable onNews) {}

        @Override
        public void stop() {}
    };
}
