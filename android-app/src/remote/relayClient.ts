// The app's side of the device channel (docs/protocolo-rele.md §10): one
// WebSocket with role=device to the Kopi relay. It knows nothing about chess;
// it keeps the connection up, tracks which PCs are in server mode
// (`presence`) and pairs each request with its answer by `id`.
//
// - Pings every 5 minutes: API Gateway closes a connection after 10 silent
//   minutes, and after 2 hours anyway; then it reconnects by itself, with a
//   growing wait (2 s … 60 s).
// - Android closes WebSockets in the background: `wake()` reconnects at once
//   when the app comes back, and asks for `presence` again.
// - A request is lost after 15 s without an answer (protocolo-remoto.md §1).

import { RemoteError, type Server } from './model';

export const RELAY_URL = 'wss://01q3xfi4m6.execute-api.eu-south-2.amazonaws.com/prod';

/** The bit of the browser's WebSocket this client uses; a fake in the tests. */
export interface SocketLike {
  onopen: ((event: unknown) => void) | null;
  onmessage: ((event: { data: unknown }) => void) | null;
  onclose: ((event: unknown) => void) | null;
  onerror: ((event: unknown) => void) | null;
  send(data: string): void;
  close(): void;
}

export type SocketFactory = (url: string) => SocketLike;

export interface Timers {
  setTimeout(callback: () => void, ms: number): unknown;
  clearTimeout(handle: unknown): void;
  setInterval(callback: () => void, ms: number): unknown;
  clearInterval(handle: unknown): void;
}

const realTimers: Timers = {
  setTimeout: (callback, ms) => globalThis.setTimeout(callback, ms),
  clearTimeout: (handle) => globalThis.clearTimeout(handle as number),
  setInterval: (callback, ms) => globalThis.setInterval(callback, ms),
  clearInterval: (handle) => globalThis.clearInterval(handle as number),
};

/** `connecting` until the socket opens; `offline` while waiting to retry; `signedOut` with no token. */
export type ConnectionState = 'connecting' | 'open' | 'offline' | 'signedOut';

export interface RelayClientOptions {
  /** A valid access token (refreshed if needed), or null when signed out. */
  token: () => Promise<string | null>;
  socket?: SocketFactory;
  timers?: Timers;
  url?: string;
  /** What the PC sees as `fromName`. */
  name?: string;
  timeoutMs?: number;
  pingMs?: number;
}

type Payload = Record<string, unknown>;

interface Pending {
  to: string;
  resolve: (payload: Payload) => void;
  reject: (error: RemoteError) => void;
  timer: unknown;
}

const FIRST_RETRY_MS = 2_000;
const LAST_RETRY_MS = 60_000;

export class RelayClient {
  private readonly options: Required<Omit<RelayClientOptions, 'name'>> & { name: string };
  private socket: SocketLike | null = null;
  private stateNow: ConnectionState = 'offline';
  private serversNow: Server[] = [];
  private readonly pending = new Map<string, Pending>();
  private readonly presenceListeners = new Set<(servers: Server[]) => void>();
  private readonly stateListeners = new Set<(state: ConnectionState) => void>();
  private nextId = 1;
  private retryMs = FIRST_RETRY_MS;
  private retryTimer: unknown = null;
  private pingTimer: unknown = null;
  private running = false;
  /** Goes up with every connection that opens: the PC knows each one apart. */
  private connectionNumber = 0;

  constructor(options: RelayClientOptions) {
    this.options = {
      socket: (url) => new WebSocket(url) as unknown as SocketLike,
      timers: realTimers,
      url: RELAY_URL,
      name: 'Android',
      timeoutMs: 15_000,
      pingMs: 5 * 60_000,
      ...options,
    };
  }

  get state(): ConnectionState {
    return this.stateNow;
  }

  /** The PCs in server mode, as the relay last said. */
  get servers(): readonly Server[] {
    return this.serversNow;
  }

  /** Which connection this is; a new one means the PC must hear `hello` again. */
  get connection(): number {
    return this.connectionNumber;
  }

  onPresence(listener: (servers: Server[]) => void): () => void {
    this.presenceListeners.add(listener);
    return () => this.presenceListeners.delete(listener);
  }

  onState(listener: (state: ConnectionState) => void): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  /** Connects, and keeps reconnecting until `stop()`. */
  start(): void {
    this.running = true;
    if (!this.socket && this.retryTimer === null) {
      void this.connect();
    }
  }

  /** Closes the connection for good (signing out). */
  stop(): void {
    this.running = false;
    this.cancelRetry();
    this.drop();
    this.setServers([]);
    this.setState('offline');
  }

  /** The app is back in the foreground: reconnect now, or refresh `presence`. */
  wake(): void {
    if (!this.running) {
      return;
    }
    if (this.socket && this.stateNow === 'open') {
      this.send({ type: 'presence' });
    } else if (!this.socket) {
      this.cancelRetry();
      this.retryMs = FIRST_RETRY_MS;
      void this.connect();
    }
  }

  /**
   * Sends `payload` to the PC with connection `to` and waits for its answer.
   * Resolves with the answer's payload when `ok` is true; otherwise rejects
   * with a RemoteError carrying the `error` code.
   */
  request(to: string, payload: Payload): Promise<Payload> {
    return new Promise((resolve, reject) => {
      if (!this.socket || this.stateNow !== 'open') {
        reject(new RemoteError('DISCONNECTED'));
        return;
      }
      const id = `q${this.nextId++}`;
      const timer = this.options.timers.setTimeout(() => this.settle(id, new RemoteError('TIMEOUT')), this.options.timeoutMs);
      this.pending.set(id, { to, resolve, reject, timer });
      this.send({ type: 'relay', to, id, payload });
    });
  }

  private async connect(): Promise<void> {
    this.setState('connecting');
    const token = await this.options.token().catch(() => null);
    if (!this.running) {
      return;
    }
    if (!token) {
      this.setState('signedOut');
      return;
    }
    const query = new URLSearchParams({ token, protocol: '1', role: 'device', name: this.options.name });
    let socket: SocketLike;
    try {
      socket = this.options.socket(`${this.options.url}?${query}`);
    } catch {
      this.retryLater();
      return;
    }
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return;
      this.retryMs = FIRST_RETRY_MS;
      this.connectionNumber++;
      this.setState('open');
      this.send({ type: 'presence' });
      this.pingTimer = this.options.timers.setInterval(() => this.send({ type: 'ping' }), this.options.pingMs);
    };
    socket.onmessage = (event) => {
      if (this.socket === socket) this.receive(event.data);
    };
    socket.onclose = () => {
      if (this.socket !== socket) return;
      this.drop();
      this.retryLater();
    };
    socket.onerror = () => {
      // onclose follows; nothing else to do.
    };
  }

  private receive(data: unknown): void {
    let message: Payload;
    try {
      message = JSON.parse(String(data)) as Payload;
    } catch {
      return;
    }
    if (typeof message !== 'object' || message === null) {
      return;
    }
    switch (message['type']) {
      case 'presence':
        this.setServers(Array.isArray(message['servers']) ? (message['servers'] as Server[]) : []);
        break;
      case 'relay':
        this.answer(message);
        break;
      case 'error':
        if (typeof message['id'] === 'string') {
          this.settle(message['id'], new RemoteError(String(message['code'] ?? 'ERROR')));
        } else if (message['code'] === 'NOT_CONNECTED') {
          // The relay forgot this connection: start a new one.
          this.drop();
          this.retryLater(0);
        }
        break;
      default:
        break; // pong, and the game notices of §7, which the app does not use
    }
  }

  private answer(message: Payload): void {
    const id = message['id'];
    const payload = message['payload'];
    const request = typeof id === 'string' ? this.pending.get(id) : undefined;
    // Only the PC that was asked may answer.
    if (!request || message['from'] !== request.to || typeof payload !== 'object' || payload === null) {
      return;
    }
    const answer = payload as Payload;
    if (answer['ok'] === true) {
      this.settle(id as string, answer);
    } else {
      const code = typeof answer['error'] === 'string' ? answer['error'] : 'INTERNAL';
      this.settle(id as string, new RemoteError(code, typeof answer['detail'] === 'string' ? answer['detail'] : code));
    }
  }

  private settle(id: string, result: Payload | RemoteError): void {
    const request = this.pending.get(id);
    if (!request) {
      return;
    }
    this.pending.delete(id);
    this.options.timers.clearTimeout(request.timer);
    if (result instanceof RemoteError) {
      request.reject(result);
    } else {
      request.resolve(result);
    }
  }

  private send(message: Payload): void {
    try {
      this.socket?.send(JSON.stringify(message));
    } catch {
      // A socket that is closing: onclose will follow.
    }
  }

  /** Forgets the socket and fails every request still waiting. */
  private drop(): void {
    const socket = this.socket;
    this.socket = null;
    if (this.pingTimer !== null) {
      this.options.timers.clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
    if (socket) {
      socket.onopen = socket.onmessage = socket.onclose = socket.onerror = null;
      try {
        socket.close();
      } catch {
        // already closed
      }
    }
    for (const id of [...this.pending.keys()]) {
      this.settle(id, new RemoteError('DISCONNECTED'));
    }
  }

  private retryLater(ms = this.retryMs): void {
    this.setState('offline');
    if (!this.running) {
      return;
    }
    this.cancelRetry();
    this.retryTimer = this.options.timers.setTimeout(() => {
      this.retryTimer = null;
      void this.connect();
    }, ms);
    this.retryMs = Math.min(this.retryMs * 2, LAST_RETRY_MS);
  }

  private cancelRetry(): void {
    if (this.retryTimer !== null) {
      this.options.timers.clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private setState(state: ConnectionState): void {
    if (state !== this.stateNow) {
      this.stateNow = state;
      this.stateListeners.forEach((listener) => listener(state));
    }
  }

  private setServers(servers: Server[]): void {
    this.serversNow = servers;
    this.presenceListeners.forEach((listener) => listener(servers));
  }
}
