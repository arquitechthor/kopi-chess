// The JSON of the remote protocol, version 1 (docs/protocolo-remoto.md §2–§4),
// as the PC's kopi-adapter-remote sends it. The app ignores fields it does
// not know, so the PC may add some without breaking it.

export type Color = 'WHITE' | 'BLACK';
export type BotProfile = 'PAWN' | 'KNIGHT' | 'BISHOP' | 'ROOK' | 'QUEEN';
export const BOT_PROFILES: readonly BotProfile[] = ['PAWN', 'KNIGHT', 'BISHOP', 'ROOK', 'QUEEN'];

export interface PlayedMove {
  ply: number;
  uci: string;
  san: string;
}

export interface Outcome {
  termination: string; // CHECKMATE, RESIGNATION, STALEMATE…
  winner: Color | null; // null for a draw
}

export interface Game {
  id: string;
  status: string; // IN_PROGRESS, FINISHED…
  botProfile: BotProfile | null;
  humanColor: Color | null;
  fen: string;
  sideToMove: Color;
  inCheck: boolean;
  humanToMove: boolean;
  moves?: PlayedMove[]; // left out in the list of games
  lastMove: PlayedMove | null;
  legalMoves: string[];
  outcome: Outcome | null;
  result: string | null;
  version: number;
  createdAt: string;
}

export interface Bot {
  profile: BotProfile;
  level: number;
  description: string;
  record: { won: number; lost: number; drawn: number; unfinished: number };
}

export interface Hello {
  kopiChessVersion: string;
  remoteVersion: number;
  name: string;
}

/** A PC in server mode, from the relay's `presence` (protocolo-rele.md §10.2). */
export interface Server {
  connectionId: string;
  name: string;
  connectedAt: string;
}

/**
 * Why a request failed: the PC's stable `error` codes (§4), the relay's
 * codes (protocolo-rele.md §10.2), and the app's own: TIMEOUT (15 s without
 * an answer), DISCONNECTED (the WebSocket closed meanwhile) and
 * SIGNED_OUT (no token).
 */
export class RemoteError extends Error {
  constructor(
    readonly code: string,
    detail = code,
  ) {
    super(detail);
    this.name = 'RemoteError';
  }
}

/** True when the request may have reached the PC: ask for the game again before retrying. */
export function isLost(error: unknown): boolean {
  return error instanceof RemoteError && ['TIMEOUT', 'DISCONNECTED', 'DEVICE_NOT_CONNECTED', 'NOT_CONNECTED', 'NO_SERVER'].includes(error.code);
}
