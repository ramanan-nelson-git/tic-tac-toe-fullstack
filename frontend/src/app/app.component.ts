import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, throwError } from 'rxjs';
import { BoardComponent } from './board.component';
import { GameApiService } from './game-api.service';
import { ApiError, GameMode, GameState, Scoreboard } from './models';
import { MoveHistoryComponent } from './move-history.component';
import { ScoreboardComponent } from './scoreboard.component';

const STORAGE_KEY = 'tic-tac-toe:gameId';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [BoardComponent, MoveHistoryComponent, ScoreboardComponent],
  template: `
    <main class="shell">
      <header class="top">
        <h1>Tic Tac Toe</h1>
        @if (game(); as g) {
          <div class="modes" role="group" aria-label="Game mode">
            <button type="button" class="btn" [attr.aria-pressed]="g.mode === 'TwoPlayer'"
                    [disabled]="busy()" (click)="setMode('TwoPlayer')">Two players</button>
            <button type="button" class="btn" [attr.aria-pressed]="g.mode === 'VsComputer'"
                    [disabled]="busy()" (click)="setMode('VsComputer')">Play the computer</button>
          </div>
        }
      </header>

      @if (error(); as message) {
        <p class="error" role="alert">
          {{ message }}
          @if (!game()) {
            <button type="button" class="btn" (click)="start()">Try again</button>
          }
        </p>
      }

      @if (game(); as g) {
        <div class="layout">
          <section>
            <p class="status" aria-live="polite"><span>{{ statusText() }}</span></p>

            <app-board
              [cells]="g.board"
              [winning]="g.winningCells"
              [locked]="busy() || g.status !== 'InProgress'"
              [hint]="g.currentPlayer"
              (cellClick)="onCell($event)"
            />

            <div class="actions">
              <button type="button" class="btn" [disabled]="!canUndo()" (click)="undo()">Undo last move</button>
              <button type="button" class="btn" [disabled]="busy()" (click)="resetGame()">Reset game</button>
            </div>

            @if (g.mode === 'VsComputer') {
              <p class="note">
                You play X and the computer plays O. Undo takes back the computer's move and your move before it.
              </p>
            }
          </section>

          <aside class="stack">
            <app-scoreboard
              [score]="scoreboard()"
              [mode]="g.mode"
              [disabled]="busy()"
              (resetScoreboard)="resetScoreboard()"
            />
            <app-move-history [moves]="g.moves" />
          </aside>
        </div>
      } @else if (!error()) {
        <p>Connecting to the game server…</p>
      }
    </main>
  `,
})
export class AppComponent implements OnInit {
  private readonly api = inject(GameApiService);

  readonly game = signal<GameState | null>(null);
  readonly scoreboard = signal<Scoreboard>({ xWins: 0, oWins: 0, draws: 0 });
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  readonly canUndo = computed(() => {
    const g = this.game();
    return !!g && g.moves.length > 0 && !this.busy();
  });

  readonly statusText = computed(() => {
    const g = this.game();
    if (!g) return '';
    const vsComputer = g.mode === 'VsComputer';

    if (g.status === 'Won') {
      if (vsComputer) return g.winner === 'X' ? 'You win!' : 'The computer wins.';
      return `Player ${g.winner} wins!`;
    }
    if (g.status === 'Draw') return "It's a draw.";
    return vsComputer ? 'Your turn (X)' : `Player ${g.currentPlayer}'s turn`;
  });

  ngOnInit(): void {
    this.start();
  }

  /** Resume the game from this browser tab if the server still has it, otherwise start a new one. */
  start(): void {
    const savedId = this.readSavedId();
    const create$ = this.api.createGame('TwoPlayer');
    const load$ = savedId
      ? this.api.getGame(savedId).pipe(
          catchError((err: unknown) =>
            err instanceof HttpErrorResponse && err.status === 404 ? create$ : throwError(() => err),
          ),
        )
      : create$;
    this.run(load$);
  }

  onCell(index: number): void {
    const g = this.game();
    if (!g || this.busy() || g.status !== 'InProgress' || g.board[index] !== null || !g.currentPlayer) return;
    this.run(this.api.makeMove(g.id, g.currentPlayer, Math.floor(index / 3), index % 3));
  }

  undo(): void {
    const g = this.game();
    if (g && this.canUndo()) this.run(this.api.undo(g.id));
  }

  resetGame(): void {
    const g = this.game();
    if (g) this.run(this.api.resetGame(g.id));
  }

  setMode(mode: GameMode): void {
    const g = this.game();
    if (g && g.mode !== mode) this.run(this.api.resetGame(g.id, mode));
  }

  resetScoreboard(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .resetScoreboard()
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (score) => this.scoreboard.set(score),
        error: (err: unknown) => this.fail(err),
      });
  }

  // ---------------------------------------------------------------------------------------------

  private run(request$: Observable<GameState>): void {
    this.busy.set(true);
    this.error.set(null);
    request$.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (state) => this.apply(state),
      error: (err: unknown) => this.fail(err),
    });
  }

  private apply(state: GameState): void {
    this.game.set(state);
    this.scoreboard.set(state.scoreboard);
    this.saveId(state.id);
  }

  private fail(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) {
        this.error.set('Cannot reach the game server. Start the backend on http://localhost:5080, then try again.');
        return;
      }
      if (err.status === 404) {
        // The server no longer knows this game (for example after a restart).
        this.game.set(null);
        this.clearSavedId();
      }
      const apiError = err.error as ApiError | null;
      this.error.set(apiError?.error ?? 'Something went wrong. Please try again.');
      return;
    }
    this.error.set('Something went wrong. Please try again.');
  }

  private readSavedId(): string | null {
    try {
      return sessionStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private saveId(id: string): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, id);
    } catch {
      /* storage unavailable: the game simply will not survive a refresh */
    }
  }

  private clearSavedId(): void {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* ignore */
    }
  }
}
