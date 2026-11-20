import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RemoteError } from './model';
import { RelayClient, type SocketLike } from './relayClient';
import { RemotePc } from './remotePc';

/** An in-memory relay: records what the app sends and lets the test answer. */
class FakeSocket implements SocketLike {
  onopen: ((event: unknown) => void) | null = null;
  onmessage: ((event: { data: unknown }) => void) | null = null;
  onclose: ((event: unknown) => void) | null = null;
  onerror: ((event: unknown) => void) | null = null;
  readonly sent: Record<string, unknown>[] = [];
  closed = false;

  constructor(readonly url: string) {}

  send(data: string): void {
    this.sent.push(JSON.parse(data));
  }

  close(): void {
    this.closed = true;
  }

  open(): void {
    this.onopen?.({});
  }

  receive(message: Record<string, unknown>): void {
    this.onmessage?.({ data: JSON.stringify({ protocol: 1, ...message }) });
  }

  serverClose(): void {
    this.onclose?.({});
  }

  last(): Record<string, unknown> {
    return this.sent[this.sent.length - 1]!;
  }
}

const PC = { connectionId: 'pc-1', name: 'PC de casa', connectedAt: '2026-11-20T10:00:00Z' };

let sockets: FakeSocket[];
let token: string | null;

function client(): RelayClient {
  return new RelayClient({
    token: async () => token,
    socket: (url) => {
      const socket = new FakeSocket(url);
      sockets.push(socket);
      return socket;
    },
  });
}

/** Starts a client and opens its first socket. */
async function connected(): Promise<{ relay: RelayClient; socket: FakeSocket }> {
  const relay = client();
  relay.start();
  await vi.advanceTimersByTimeAsync(0);
  const socket = sockets[0]!;
  socket.open();
  return { relay, socket };
}

beforeEach(() => {
  vi.useFakeTimers();
  sockets = [];
  token = 'access-token';
});

afterEach(() => {
  vi.useRealTimers();
});

describe('RelayClient', () => {
  it('connects as a device with the access token', async () => {
    const { relay, socket } = await connected();
    const url = new URL(socket.url);
    expect(url.searchParams.get('token')).toBe('access-token');
    expect(url.searchParams.get('role')).toBe('device');
    expect(url.searchParams.get('protocol')).toBe('1');
    expect(relay.state).toBe('open');
  });

  it('asks for presence on opening and follows what the relay pushes', async () => {
    const { relay, socket } = await connected();
    expect(socket.sent).toEqual([{ type: 'presence' }]);
    const seen: unknown[] = [];
    relay.onPresence((servers) => seen.push(servers));
    socket.receive({ type: 'presence', servers: [PC] });
    socket.receive({ type: 'presence', servers: [] });
    expect(seen).toEqual([[PC], []]);
    expect(relay.servers).toEqual([]);
  });

  it('does not connect without a token', async () => {
    token = null;
    const relay = client();
    relay.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(sockets).toHaveLength(0);
    expect(relay.state).toBe('signedOut');
  });

  it('pairs each answer with its request by id', async () => {
    const { relay, socket } = await connected();
    const first = relay.request('pc-1', { v: 1, op: 'bots' });
    const second = relay.request('pc-1', { v: 1, op: 'games' });
    const [a, b] = socket.sent.slice(1) as { id: string }[];
    expect(socket.sent[1]).toMatchObject({ type: 'relay', to: 'pc-1', payload: { v: 1, op: 'bots' } });
    socket.receive({ type: 'relay', from: 'pc-1', id: b!.id, payload: { v: 1, ok: true, games: [] } });
    socket.receive({ type: 'relay', from: 'pc-1', id: a!.id, payload: { v: 1, ok: true, bots: [] } });
    await expect(first).resolves.toEqual({ v: 1, ok: true, bots: [] });
    await expect(second).resolves.toEqual({ v: 1, ok: true, games: [] });
  });

  it('ignores an answer from another connection', async () => {
    const { relay, socket } = await connected();
    const request = relay.request('pc-1', { v: 1, op: 'bots' });
    const { id } = socket.last() as { id: string };
    socket.receive({ type: 'relay', from: 'someone-else', id, payload: { v: 1, ok: true } });
    const caught = request.catch((error: unknown) => error);
    await vi.advanceTimersByTimeAsync(15_000);
    expect(await caught).toEqual(new RemoteError('TIMEOUT'));
  });

  it("rejects with the PC's error code", async () => {
    const { relay, socket } = await connected();
    const request = relay.request('pc-1', { v: 1, op: 'move' });
    const { id } = socket.last() as { id: string };
    socket.receive({ type: 'relay', from: 'pc-1', id, payload: { v: 1, ok: false, error: 'CONFLICT', detail: 'stale' } });
    await expect(request).rejects.toMatchObject({ code: 'CONFLICT', message: 'stale' });
  });

  it("rejects with the relay's error code", async () => {
    const { relay, socket } = await connected();
    const request = relay.request('pc-1', { v: 1, op: 'bots' });
    const { id } = socket.last() as { id: string };
    socket.receive({ type: 'error', code: 'DEVICE_NOT_CONNECTED', id });
    await expect(request).rejects.toMatchObject({ code: 'DEVICE_NOT_CONNECTED' });
  });

  it('gives a request up after 15 seconds', async () => {
    const { relay } = await connected();
    const request = relay.request('pc-1', { v: 1, op: 'bots' });
    const caught = request.catch((error: RemoteError) => error.code);
    await vi.advanceTimersByTimeAsync(14_999);
    await vi.advanceTimersByTimeAsync(1);
    expect(await caught).toBe('TIMEOUT');
  });

  it('fails the waiting requests and reconnects when the socket closes', async () => {
    const { relay, socket } = await connected();
    const request = relay.request('pc-1', { v: 1, op: 'bots' });
    socket.serverClose();
    await expect(request).rejects.toMatchObject({ code: 'DISCONNECTED' });
    expect(relay.state).toBe('offline');
    await vi.advanceTimersByTimeAsync(2_000);
    expect(sockets).toHaveLength(2);
    sockets[1]!.open();
    expect(relay.state).toBe('open');
    expect(relay.connection).toBe(2);
  });

  it('waits longer after each failed attempt, up to a minute', async () => {
    const { socket } = await connected();
    socket.serverClose();
    for (const wait of [2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000]) {
      const before = sockets.length;
      await vi.advanceTimersByTimeAsync(wait - 1);
      expect(sockets).toHaveLength(before);
      await vi.advanceTimersByTimeAsync(1);
      expect(sockets).toHaveLength(before + 1);
      sockets[sockets.length - 1]!.serverClose();
    }
  });

  it('reconnects at once on waking up', async () => {
    const { relay, socket } = await connected();
    socket.serverClose();
    relay.wake();
    await vi.advanceTimersByTimeAsync(0);
    expect(sockets).toHaveLength(2);
  });

  it('asks for presence again on waking up while connected', async () => {
    const { relay, socket } = await connected();
    relay.wake();
    expect(socket.sent).toEqual([{ type: 'presence' }, { type: 'presence' }]);
  });

  it('pings every 5 minutes', async () => {
    const { socket } = await connected();
    await vi.advanceTimersByTimeAsync(5 * 60_000);
    expect(socket.last()).toEqual({ type: 'ping' });
  });

  it('stops for good', async () => {
    const { relay, socket } = await connected();
    relay.stop();
    expect(socket.closed).toBe(true);
    await vi.advanceTimersByTimeAsync(120_000);
    expect(sockets).toHaveLength(1);
    await expect(relay.request('pc-1', {})).rejects.toMatchObject({ code: 'DISCONNECTED' });
  });
});

describe('RemotePc', () => {
  /** Answers every request on the socket with `answer(payload)`. */
  function autoAnswer(socket: FakeSocket, answer: (payload: Record<string, unknown>) => Record<string, unknown>): void {
    const send = socket.send.bind(socket);
    socket.send = (data: string) => {
      send(data);
      const message = JSON.parse(data) as { type: string; to: string; id: string; payload: Record<string, unknown> };
      if (message.type === 'relay') {
        queueMicrotask(() =>
          socket.receive({ type: 'relay', from: message.to, id: message.id, payload: { v: 1, ok: true, ...answer(message.payload) } }),
        );
      }
    };
  }

  it('says hello once per connection before the first operation', async () => {
    const { relay, socket } = await connected();
    socket.receive({ type: 'presence', servers: [PC] });
    autoAnswer(socket, (payload) => (payload['op'] === 'bots' ? { bots: [] } : { name: 'PC de casa' }));
    const pc = new RemotePc(relay, 'PC de casa', 'es', '1.2.0');
    await pc.bots();
    await pc.bots();
    const ops = socket.sent.filter((m) => m['type'] === 'relay').map((m) => m['payload']);
    expect(ops).toEqual([
      { v: 1, op: 'hello', lang: 'es', appVersion: '1.2.0' },
      { v: 1, op: 'bots' },
      { v: 1, op: 'bots' },
    ]);
  });

  it('follows the PC to its new connection after it reconnects', async () => {
    const { relay, socket } = await connected();
    socket.receive({ type: 'presence', servers: [PC] });
    autoAnswer(socket, () => ({ game: { id: 'g' } }));
    const pc = new RemotePc(relay, 'PC de casa', 'en', '1.2.0');
    await pc.game('g');
    socket.receive({ type: 'presence', servers: [{ ...PC, connectionId: 'pc-2' }] });
    await pc.move({ id: 'g', version: 4 }, 'e2e4');
    const relays = socket.sent.filter((m) => m['type'] === 'relay');
    expect(relays.map((m) => [m['to'], (m['payload'] as { op: string }).op])).toEqual([
      ['pc-1', 'hello'],
      ['pc-1', 'game'],
      ['pc-2', 'hello'],
      ['pc-2', 'move'],
    ]);
    expect(relays[3]!['payload']).toEqual({ v: 1, op: 'move', gameId: 'g', uci: 'e2e4', version: 4 });
  });

  it('fails without asking when the PC is not in server mode', async () => {
    const { relay, socket } = await connected();
    const pc = new RemotePc(relay, 'PC de casa', 'en', '1.2.0');
    await expect(pc.games()).rejects.toMatchObject({ code: 'NO_SERVER' });
    expect(socket.sent).toEqual([{ type: 'presence' }]);
  });
});
