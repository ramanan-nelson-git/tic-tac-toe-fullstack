import { Component, computed, input, output } from '@angular/core';
import { GameMode, Scoreboard } from './models';

@Component({
  selector: 'app-scoreboard',
  standalone: true,
  template: `
    <section class="sheet">
      <h2>Scoreboard</h2>
      <dl class="tally">
        <div>
          <dd>{{ score().xWins }}</dd>
          <dt>{{ xLabel() }}</dt>
        </div>
        <div>
          <dd>{{ score().draws }}</dd>
          <dt>Draws</dt>
        </div>
        <div>
          <dd class="o">{{ score().oWins }}</dd>
          <dt>{{ oLabel() }}</dt>
        </div>
      </dl>
      <button type="button" class="btn" [disabled]="disabled()" (click)="resetScoreboard.emit()">
        Reset scoreboard
      </button>
    </section>
  `,
  styles: [
    `
      .tally { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 0 0 16px; }
      .tally div { display: flex; flex-direction: column-reverse; }
      dd { margin: 0; font-family: var(--serif); font-size: 2.4rem; line-height: 1; font-variant-numeric: tabular-nums; }
      dd.o { color: var(--teal); }
      dt { color: var(--ink-soft); font-size: 0.9rem; }
    `,
  ],
})
export class ScoreboardComponent {
  readonly score = input.required<Scoreboard>();
  readonly mode = input.required<GameMode>();
  readonly disabled = input(false);
  readonly resetScoreboard = output<void>();

  readonly xLabel = computed(() => (this.mode() === 'VsComputer' ? 'You (X)' : 'Player X'));
  readonly oLabel = computed(() => (this.mode() === 'VsComputer' ? 'Computer (O)' : 'Player O'));
}
