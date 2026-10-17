 import { Component, input, output } from '@angular/core';
import { Player } from './models';

@Component({
  selector: 'app-board',
  standalone: true,
  template: `
    <div class="board" role="group" aria-label="Tic Tac Toe board">
      @for (cell of cells(); track $index) {
        <button
          type="button"
          class="cell"
          [class.win]="winning().includes($index)"
          [disabled]="locked() || cell !== null"
          [attr.aria-label]="label($index, cell)"
          [attr.data-hint]="locked() ? '' : hint()"
          (click)="cellClick.emit($index)"
        >
          @if (cell === 'X') {
            <svg class="mark x" viewBox="0 0 100 100" aria-hidden="true">
              <path d="M26 26 L74 74" pathLength="100" />
              <path d="M74 26 L26 74" pathLength="100" />
            </svg>
          } @else if (cell === 'O') {
            <svg class="mark o" viewBox="0 0 100 100" aria-hidden="true">
              <circle cx="50" cy="50" r="27" pathLength="100" />
            </svg>
          }
        </button>
      }
    </div>
  `,
  styles: [
    `
      .board {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        width: 100%;
        aspect-ratio: 1;
        background: var(--paper);
      }
      .cell {
        position: relative;
        display: grid;
        place-items: center;
        padding: 0;
        margin: 0;
        border: 0;
        background: transparent;
        color: var(--ink);
        font-family: var(--serif);
        cursor: pointer;
      }
      .cell:disabled { cursor: default; }
      .cell:not(:nth-child(3n)) { border-right: 4px solid var(--ink); }
      .cell:nth-child(-n + 6) { border-bottom: 4px solid var(--ink); }

      /* faint preview of the mark that a click would place */
      .cell:not(:disabled):hover::after {
        content: attr(data-hint);
        position: absolute;
        inset: 0;
        display: grid;
        place-items: center;
        font-size: clamp(2.5rem, 9vw, 4.5rem);
        opacity: 0.18;
      }

      .cell.win {
        background: linear-gradient(transparent 14%, var(--marker) 14% 86%, transparent 86%);
      }

      .mark { width: 62%; height: 62%; }
      .mark path,
      .mark circle {
        fill: none;
        stroke-width: 9;
        stroke-linecap: round;
        stroke-dasharray: 100;
        stroke-dashoffset: 100;
        animation: draw 0.32s ease-out forwards;
      }
      .x path { stroke: var(--ink); }
      .o circle { stroke: var(--teal); }
      .mark path:nth-child(2) { animation-delay: 0.18s; }

      @keyframes draw {
        to { stroke-dashoffset: 0; }
      }
      @media (prefers-reduced-motion: reduce) {
        .mark path,
        .mark circle { animation: none; stroke-dashoffset: 0; }
      }
    `,
  ],
})
export class BoardComponent {
  readonly cells = input.required<(Player | null)[]>();
  readonly winning = input<number[]>([]);
  /** True when the game is over or a request is in flight. */
  readonly locked = input(false);
  /** Mark shown as a hover preview on empty cells. */
  readonly hint = input<Player | null>(null);
  readonly cellClick = output<number>();

  label(index: number, cell: Player | null): string {
    return `Row ${Math.floor(index / 3) + 1}, column ${(index % 3) + 1}, ${cell ?? 'empty'}`;
  }
}
