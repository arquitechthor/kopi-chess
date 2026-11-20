// The operations of the remote protocol (docs/protocolo-remoto.md §2), typed,
// over the device channel. The PC is found by name: its connectionId changes
// every time it reconnects, so each request goes to whichever connection of
// that name the relay last reported.

import { RemoteError, type Bot, type BotProfile, type Color, type Game, type Hello, type Server } from './model';
import type { RelayClient } from './relayClient';

export const REMOTE_VERSION = 1;

export type HumanColor = Color | 'RANDOM';

export class RemotePc {
  /** "connection:connectionId" of the last `hello`: the PC keeps the language per connection. */
  private greeted: string | null = null;

  constructor(
    private readonly relay: RelayClient,
    readonly name: string,
    private readonly lang: string,
    private readonly appVersion: string,
  ) {}

  /** This PC's connection, or null while it is not in server mode. */
  get server(): Server | null {
    return this.relay.servers.find((server) => server.name === this.name) ?? null;
  }

  async hello(): Promise<Hello> {
    const answer = await this.send('hello', { lang: this.lang, appVersion: this.appVersion }, false);
    return answer as unknown as Hello;
  }

  async bots(): Promise<Bot[]> {
    return (await this.send('bots')).bots as Bot[];
  }

  async games(): Promise<Game[]> {
    return (await this.send('games')).games as Game[];
  }

  async newGame(botProfile: BotProfile, humanColor: HumanColor): Promise<Game> {
    return (await this.send('new', { botProfile, humanColor })).game as Game;
  }

  async game(gameId: string): Promise<Game> {
    return (await this.send('game', { gameId })).game as Game;
  }

  async move(game: Pick<Game, 'id' | 'version'>, uci: string): Promise<Game> {
    return (await this.send('move', { gameId: game.id, uci, version: game.version })).game as Game;
  }

  async resign(game: Pick<Game, 'id' | 'version'>): Promise<Game> {
    return (await this.send('resign', { gameId: game.id, version: game.version })).game as Game;
  }

  private async send(op: string, fields: Record<string, unknown> = {}, greetFirst = true): Promise<Record<string, unknown>> {
    const server = this.server;
    if (!server) {
      throw new RemoteError('NO_SERVER');
    }
    const key = `${this.relay.connection}:${server.connectionId}`;
    if (greetFirst && this.greeted !== key) {
      await this.hello();
    }
    const answer = await this.relay.request(server.connectionId, { v: REMOTE_VERSION, op, ...fields });
    if (op === 'hello') {
      this.greeted = key;
    }
    return answer;
  }
}
