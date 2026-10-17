import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { GameMode, GameState, Player, Scoreboard } from './models';

/** Thin wrapper over the REST API. All game rules live on the server. */
@Injectable({ providedIn: 'root' })
export class GameApiService {
  private readonly http = inject(HttpClient);

  createGame(mode: GameMode): Observable<GameState> {
    return this.http.post<GameState>(`${API_BASE_URL}/games`, { mode });
  }

  getGame(id: string): Observable<GameState> {
    return this.http.get<GameState>(`${API_BASE_URL}/games/${id}`);
  }

  makeMove(id: string, player: Player, row: number, column: number): Observable<GameState> {
    return this.http.post<GameState>(`${API_BASE_URL}/games/${id}/moves`, { player, row, column });
  }

  undo(id: string): Observable<GameState> {
    return this.http.post<GameState>(`${API_BASE_URL}/games/${id}/undo`, {});
  }

  resetGame(id: string, mode?: GameMode): Observable<GameState> {
    return this.http.post<GameState>(`${API_BASE_URL}/games/${id}/reset`, mode ? { mode } : {});
  }

  getScoreboard(): Observable<Scoreboard> {
    return this.http.get<Scoreboard>(`${API_BASE_URL}/scoreboard`);
  }

  resetScoreboard(): Observable<Scoreboard> {
    return this.http.post<Scoreboard>(`${API_BASE_URL}/scoreboard/reset`, {});
  }
}
