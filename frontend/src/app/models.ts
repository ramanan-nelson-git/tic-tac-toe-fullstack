export type Player = 'X' | 'O';
export type GameMode = 'TwoPlayer' | 'VsComputer';
export type GameStatus = 'InProgress' | 'Won' | 'Draw';

export interface MoveDto {
  moveNumber: number;
  player: Player;
  /** Zero-based. */
  row: number;
  /** Zero-based. */
  column: number;
}

export interface Scoreboard {
  xWins: number;
  oWins: number;
  draws: number;
}

export interface GameState {
  id: string;
  /** 9 cells, row-major. */
  board: (Player | null)[];
  /** null once the game is over. */
  currentPlayer: Player | null;
  mode: GameMode;
  status: GameStatus;
  winner: Player | null;
  /** Flat cell indexes of the winning line. */
  winningCells: number[];
  moves: MoveDto[];
  scoreboard: Scoreboard;
}

export interface ApiError {
  status: number;
  error: string;
}
