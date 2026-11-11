package com.arquitechthor.kopi.application.port;

/// The connection to the relay as a "server" (ADR-022,
/// `docs/protocolo-rele.md` §10): while serving, the Android app of the
/// same Kopi account can reach this computer, and its requests go to the
/// [RemoteRequestHandler].
///
/// Off by default. It only connects while there is a Kopi session, like
/// the relay's notifications.
public interface ServerConnection {

    /// Starts serving, or changes the name, shown to the app as
    /// `name` (at most 40 characters; longer names are cut).
    void serve(String name);

    /// Stops serving: back to the relay's notifications only. A request
    /// that still arrives gets `SERVER_MODE_OFF`.
    void stopServing();

    /// Whether it is serving now (asked for, not necessarily connected).
    boolean serving();

    /// Never serves.
    ServerConnection NONE = new ServerConnection() {
        @Override
        public void serve(String name) {}

        @Override
        public void stopServing() {}

        @Override
        public boolean serving() {
            return false;
        }
    };
}
