import { Component, input } from '@angular/core';
import { MoveDto } from './models';

@Component({
  selector: 'app-move-history',
  standalone: true,
  template: `
    <section class="sheet">
      <h2>Move history</h2>
      @if (moves().length === 0) {
        <p class="empty">No moves yet. Pick a square to start.</p>
      } @else {
        <div class="scroll">
          <table>
            <thead>
              <tr>
                <th scope="col">Move</th>
                <th scope="col">Player</th>
                <th scope="col">Position</th>
              </tr>
            </thead>
            <tbody>
              @for (move of moves(); track move.moveNumber) {
                <tr>
                  <td>{{ move.moveNumber }}</td>
                  <td>
                    <span class="mark" [class.o]="move.player === 'O'">{{ move.player }}</span>
                  </td>
                  <td>Row {{ move.row + 1 }}, Column {{ move.column + 1 }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
  styles: [
    `
      .empty { margin: 0; color: var(--ink-soft); }
      .scroll { max-height: 260px; overflow-y: auto; }
      table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
      th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--grid); }
      th { font-weight: 600; color: var(--ink-soft); position: sticky; top: 0; background: var(--paper); }
      .mark { font-family: var(--serif); font-weight: 700; color: var(--ink); }
      .mark.o { color: var(--teal); }
    `,
  ],
})
export class MoveHistoryComponent {
  readonly moves = input.required<MoveDto[]>();
}
