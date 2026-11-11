package com.arquitechthor.kopi.bootstrap;

import java.net.InetAddress;
import java.net.UnknownHostException;

/// This computer's own name: what the Android app shows when the person
/// gives server mode no name of their own (ADR-022).
final class ComputerName {

    private ComputerName() {}

    static String local() {
        var name = System.getenv("COMPUTERNAME"); // Windows
        if (name == null || name.isBlank()) {
            name = System.getenv("HOSTNAME");
        }
        if (name == null || name.isBlank()) {
            try {
                name = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException _) {
                name = "";
            }
        }
        return name;
    }
}
