package com.arquitechthor.kopi.adapter.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/// A fake relay WebSocket (`docs/protocolo-rele.md` §7, §10) on a local
/// port: just enough of RFC 6455 for the JDK's client (handshake, text
/// frames, close). Tests see each connection's query and messages, and send
/// messages to the newest connection.
final class FakeRelay implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /// One connection from the PC.
    static final class Connection {
        final String query;
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private final Socket socket;

        Connection(String query, Socket socket) {
            this.query = query;
            this.socket = socket;
        }

        /// The next message from the PC, waiting up to 5 seconds.
        String next() throws InterruptedException {
            var message = received.poll(5, TimeUnit.SECONDS);
            if (message == null) {
                throw new AssertionError("Nothing received from the PC");
            }
            return message;
        }

        synchronized void send(String text) throws IOException {
            var payload = text.getBytes(StandardCharsets.UTF_8);
            var out = socket.getOutputStream();
            out.write(0x81);
            if (payload.length < 126) {
                out.write(payload.length);
            } else if (payload.length < 65_536) {
                out.write(126);
                out.write(payload.length >>> 8);
                out.write(payload.length & 0xFF);
            } else {
                out.write(127);
                for (int shift = 56; shift >= 0; shift -= 8) {
                    out.write((int) ((long) payload.length >>> shift) & 0xFF);
                }
            }
            out.write(payload);
            out.flush();
        }

        boolean isClosed() {
            return socket.isClosed();
        }
    }

    private final ServerSocket server;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Connection> opened = new LinkedBlockingQueue<>();

    FakeRelay() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(this::accept);
    }

    URI uri() {
        return URI.create("ws://127.0.0.1:" + server.getLocalPort() + "/prod");
    }

    /// The next connection the PC opens, waiting up to 5 seconds.
    Connection nextConnection() throws InterruptedException {
        var connection = opened.poll(5, TimeUnit.SECONDS);
        if (connection == null) {
            throw new AssertionError("The PC did not connect");
        }
        return connection;
    }

    List<Connection> connections() {
        return new ArrayList<>(connections);
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (var connection : connections) {
            connection.socket.close();
        }
    }

    private void accept() {
        while (!server.isClosed()) {
            try {
                var socket = server.accept();
                Thread.ofVirtual().start(() -> serve(socket));
            } catch (IOException _) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            var in = socket.getInputStream();
            var request = readHead(in);
            var lines = request.split("\r\n");
            var target = lines[0].split(" ")[1];
            String key = null;
            for (var line : lines) {
                if (line.toLowerCase().startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).strip();
                }
            }
            var accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                    .digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            var connection = new Connection(URI.create(target).getRawQuery(), socket);
            connections.add(connection);
            opened.add(connection);
            readFrames(in, connection);
        } catch (IOException | NoSuchAlgorithmException _) {
            // connection gone
        }
    }

    private static String readHead(InputStream in) throws IOException {
        var head = new StringBuilder();
        while (head.length() < 4 || !head.substring(head.length() - 4).equals("\r\n\r\n")) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("closed during the handshake");
            }
            head.append((char) b);
        }
        return head.toString();
    }

    private static void readFrames(InputStream in, Connection connection) throws IOException {
        var message = new ByteArrayOutputStream();
        while (true) {
            int first = in.read();
            int second = in.read();
            if (first < 0 || second < 0) {
                return;
            }
            long length = second & 0x7F;
            if (length == 126) {
                length = (in.read() << 8) | in.read();
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | in.read();
                }
            }
            var mask = (second & 0x80) != 0 ? in.readNBytes(4) : null;
            var payload = in.readNBytes((int) length);
            if (mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }
            int opcode = first & 0x0F;
            if (opcode == 8) {
                return; // close
            }
            if (opcode == 1 || opcode == 0) {
                message.write(payload);
                if ((first & 0x80) != 0) {
                    connection.received.add(message.toString(StandardCharsets.UTF_8));
                    message.reset();
                }
            }
        }
    }
}
