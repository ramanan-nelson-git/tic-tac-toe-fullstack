# Tic Tac Toe — Angular + Java (Spring Boot)

## 1. Project overview

A browser-based Tic Tac Toe game for the Round 2 exercise. The **Java backend** owns all game state (board,
turns, move history, status, scoreboard) and enforces the rules. The **Angular frontend** only renders the
latest state returned by the REST API and sends the player's clicks.

## 2. Tech stack

| Layer | Technology |
| --- | --- |
| Frontend | Angular 19 (standalone components, signals), TypeScript |
| Backend | Java 17+, Spring Boot 3.3 (Spring Web), Maven |
| API | REST, JSON |
| Storage | In-memory (lost when the backend restarts) |
| Logging | Log4j2 (console + rolling file) |
| Monitoring | Spring Boot Actuator, Micrometer, Prometheus |
| Tests | JUnit 5 + Spring MockMvc |

## 3. Features implemented

- 3 × 3 board; empty cells are clickable, taken cells are locked
- Two Player mode and Play Against Computer mode (human is X, computer is O)
- Clear "whose turn" display; turns alternate only after a valid move
- Win detection (row, column, diagonal) with highlighted winning cells; moves blocked after the game ends
- Draw detection
- Move history (move number, player, row/column) updated after every valid move
- Undo Last Move, disabled when there is nothing to undo; mode-aware (see section 9)
- Session scoreboard served by the backend (X wins, O wins, draws), updated once per completed game
- Reset Game (keeps scoreboard) and a separate Reset Scoreboard
- Computer priority: win → block → centre → corner → any free cell
- Consistent JSON errors for invalid moves (400 / 404 / 409)
- Log4j2 logging of game events, Actuator health/metrics endpoints, and a Prometheus scrape setup with game-specific metrics (see "Logging and monitoring")

## 4. Run the backend

Requires **JDK 17 or newer** and **Maven 3.9+**.

```bash
cd backend
mvn spring-boot:run
```

The API listens on **http://localhost:5080**. Quick check: `curl -X POST localhost:5080/api/games`.

## 5. Run the frontend

Requires **Node.js 18.19+ / 20 / 22** and npm.

```bash
cd frontend
npm install
npm start
```

Open **http://localhost:4200**. The backend allows CORS from `localhost:4200`. If you change a port, update
`frontend/src/app/api.config.ts` and `backend/src/main/java/com/example/tictactoe/api/CorsConfig.java`.
Start the backend first; if it is down the UI shows a message with a **Try again** button.

## 6. API endpoint summary

Base URL `http://localhost:5080/api`. JSON, camelCase. **Rows and columns are zero-based** (the UI shows them
one-based as "Row 1, Column 1"). Enum values on the wire: modes `TwoPlayer` / `VsComputer`, statuses
`InProgress` / `Won` / `Draw`, players `X` / `O`.

| Method | Endpoint | Body | Purpose |
| --- | --- | --- | --- |
| POST | `/games` | `{ "mode": "TwoPlayer" \| "VsComputer" }` (optional, default `TwoPlayer`) | Create a game session → `201` + game state |
| GET | `/games/{id}` | | Get current game state |
| POST | `/games/{id}/moves` | `{ "player": "X" \| "O", "row": 0-2, "column": 0-2 }` | Submit a move → game state |
| POST | `/games/{id}/undo` | | Undo last move (mode-aware) → game state |
| POST | `/games/{id}/reset` | `{ "mode": ... }` (optional; omit to keep the mode) | Fresh game, same id, scoreboard untouched |
| GET | `/scoreboard` | | `{ "xWins", "oWins", "draws" }` |
| POST | `/scoreboard/reset` | | Zero the scoreboard → scoreboard |

The game id is in the URL path of every game request, so the move body does not repeat it.

### Game state response

```json
{
  "id": "6f0c1c52-3c2e-4b5e-9a39-0a3c5f0b7d11",
  "board": ["X", null, null, null, "O", null, null, null, null],
  "currentPlayer": "X",
  "mode": "VsComputer",
  "status": "InProgress",
  "winner": null,
  "winningCells": [],
  "moves": [
    { "moveNumber": 1, "player": "X", "row": 0, "column": 0 },
    { "moveNumber": 2, "player": "O", "row": 1, "column": 1 }
  ],
  "scoreboard": { "xWins": 0, "oWins": 0, "draws": 0 }
}
```

- `board` has 9 cells, row-major (`index = row * 3 + column`); `null` means empty.
- `winningCells` holds the winning line's cell indexes when `status` is `Won`.
- `currentPlayer` is `null` once the game is over.
- In `VsComputer` mode the move response already includes the computer's reply.

### Errors

Body: `{ "status": 409, "error": "That cell is already taken." }`. A rejected request never changes state.

| Status | When |
| --- | --- |
| 400 | Row/column outside 0–2, missing `row`/`column`/`player`, invalid `player` or `mode` value, malformed JSON, malformed game id |
| 404 | Unknown game id |
| 409 | Occupied cell, wrong player's turn, move after the game finished, human trying to play O in computer mode, undo with nothing to undo |

## 7. Run the tests

```bash
cd backend
mvn test
```

| Requirement | Covered by |
| --- | --- |
| Valid move, turn switching | `GameServiceTest` (valid move, turns alternate) |
| Invalid move | occupied cell, wrong turn, outside board (service + HTTP 400/409) |
| Row / column / diagonal win | `GameEngineTest` (all 8 lines), `GameServiceTest` (row, column, diagonal) |
| Draw | `GameEngineTest`, `GameServiceTest` |
| Move after game completion | `GameServiceTest` (win and draw) |
| Reset game | `GameServiceTest` |
| Undo, two-player / computer mode | `GameServiceTest` (including undo after a win/draw and after a full computer game) |
| Scoreboard update | counted once, reverted by undo, kept by reset, separate reset |
| Computer move selection | `GameEngineTest` (win, block, centre, corner, any cell, win beats block) |
| API contract | `GameApiTest` (JSON shape, enum values, status codes) |
| Metrics and Actuator | `GameServiceTest` (counters and scoreboard gauges), `ActuatorTest` (`/actuator/health`, `/actuator/prometheus`) |

There are no frontend unit tests (see section 11).

## 8. AI tools and prompt summary

- **Tool:** Claude (Anthropic) generated the first version of the code, tests and this README.
- **Prompt flow:**
  1. Gave Claude the problem statement and asked for a full solution.
  2. Claude turned the requirements into a design: backend owns state, moves are the only stored data,
     derived board/status, mode-aware undo, scoreboard reconciliation.
  3. Generated the Spring Boot API, the Angular app and the tests; checked the Java rules code by compiling it
     and running a scripted scenario run.
- **What I changed manually:**
  1. Added Log4j2 for logging purposes
  2. Added Micrometer and Prometheus for monitoring and metrics collection
- **What I reviewed most carefully:** 
  1. **Full code walk through and understood the flow as per the requirement**
  2. Undo rules in computer mode
  2. Scoreboard update-once logic
  3. Move validation order
  4. 

## 9. Design decisions

- **Backend is the source of truth.** The frontend never decides a winner or a computer move. It skips requests
  that are obviously invalid (full cell, finished game) and the backend re-validates everything.
- **Moves are the only stored state.** Board, current player, status, winner and winning cells are derived from
  the move list on every request. This makes undo trivially correct: remove moves, recompute.
- **Undo.** Two Player removes one move. Computer mode removes the computer's move and your previous move, so
  it is X's turn again. If the game ended on your move (win or draw) the computer never replied, so only your
  move is removed.
- **Computer move logic** lives in `GameEngine` as a pure function and is deterministic (first free corner,
  first free cell), so it is easy to test.
- **Concurrency:** service methods are `synchronized`, which keeps game and scoreboard updates atomic. Fine for
  a local app.
- **Scoreboard scope:** one session-level scoreboard on the server, shared by all games created since it started.
- **Switching mode** mid-game resets the board (`POST /reset` with a `mode`), because the two modes have
  different turn and undo rules. The scoreboard is kept.
- **Frontend:** Angular signals, three small presentational components (board, scoreboard, move history), one
  typed API service. The game id is kept in `sessionStorage` so a refresh resumes the game; if the server no
  longer knows it, a new game starts.

## 10. Trade-offs

| Choice | What it buys | What it costs |
| --- | --- | --- |
| **State derived from the move list** (board, status, current player recomputed each time) | Undo, history and win/draw status can never drift out of sync | Recomputes on every request, which is trivial for 9 cells |
| **Backend owns all rules; the UI has no optimistic updates** | One source of truth; the UI never shows a state the server would reject | Every click is a network round trip (invisible locally, noticeable on a slow network) |
| **Computer reply returned in the same response as the human move** | One request per turn; no polling or extra endpoint | The UI cannot show your move first and then the computer "thinking" |
| **Undo allowed after a game ends (Option B)** | Better usability: you can take back the losing move | More logic: each game remembers what it added to the scoreboard and the service reconciles it after every change. Option A would have been simpler |
| **Reset Scoreboard also clears what finished games contributed** | A later undo cannot push a counter below zero | One extra step in the service |
| **In-memory storage, `synchronized` service methods** | Simple, correct, easy for the panel to run | Restart loses everything; games are never evicted; a single lock would not scale |
| **Rule-based computer (win, block, centre, corner, any) instead of minimax** | Matches the spec exactly; deterministic and easy to test | Not unbeatable: a human can set up a fork it will not prevent |
| **One session-wide scoreboard** | Matches the spec; simple | Every browser tab shares the same scoreboard |
| **Switching mode mid-game resets the board** | The two modes have different turn and undo rules, so converting a game in progress would be messy | You lose the current game when you switch |
| **Test effort focused on the backend** | Rules, state transitions and HTTP status codes are covered | No Angular unit tests (see Known limitations) |

## 11. Clarifications and assumptions

- **Scoreboard and Undo: Option B, undo is allowed after completion.** Undoing a won or drawn game reopens it
  and takes its result off the scoreboard. Replaying to the end counts it again. The service does this by
  reconciling each game's current outcome with what it already contributed, so a completed game is counted
  exactly once.
- **Reset Game** keeps the scoreboard unchanged: that game was completed and stays counted. Undo after Reset
  cannot take the earlier result back, because the board is empty.
- **Reset Scoreboard** also forgets what finished games contributed, so a later undo cannot push a counter below zero.
- Row and column are zero-based in the API and shown one-based in the UI.
- In Computer mode the human always plays X and goes first.

## 12. Known limitations

- In-memory storage: restarting the backend clears all games and the scoreboard.
- Games are never evicted from memory (fine for a local exercise).
- The scoreboard is shared by every browser tab talking to the same backend.
- No authentication, and no live sync between two tabs.
- The Angular app has no unit tests yet. Backend tests cover the rules and the API.
- The computer is rule-based, not unbeatable.
- Metrics live in memory: counters restart from zero when the backend restarts, and the Actuator endpoints are unauthenticated.

## 13. Future improvements

SQLite persistence, a minimax "hard" computer mode, game eviction/expiry, Angular component and service tests,
OpenAPI documentation, a Grafana dashboard on top of the Prometheus metrics, Docker Compose for one-command startup
of the whole stack.

## 14. Logging and monitoring

### Logging (Log4j2)

Spring Boot's default Logback is replaced by **Log4j2** (`spring-boot-starter-log4j2`; the logging starter is
excluded in `backend/pom.xml`). Configuration is `backend/src/main/resources/log4j2-spring.xml`:

- Console and a rolling file at `backend/logs/tic-tac-toe.log` (daily or 10 MB, 7 files kept; `logs/` is git-ignored)
- `com.example.tictactoe` logs at **DEBUG**, everything else at INFO. Change the levels in the XML file.
- What is logged: game created, every accepted move (DEBUG, including the computer's reply), undo, reset, game
  finished or reopened (with the scoreboard), scoreboard reset, and every rejected request (WARN).

```
2026-10-16 10:15:02.411 INFO  [http-nio-5080-exec-2] c.e.t.s.GameService - Created game 87a1... in mode VS_COMPUTER
2026-10-16 10:15:04.002 DEBUG [http-nio-5080-exec-3] c.e.t.s.GameService - Game 87a1...: X played cell 0 (move 1)
2026-10-16 10:15:04.003 DEBUG [http-nio-5080-exec-3] c.e.t.s.GameService - Game 87a1...: O played cell 4 (move 2)
2026-10-16 10:15:09.870 WARN  [http-nio-5080-exec-5] c.e.t.a.ApiExceptionHandler - Rejected request (409): That cell is already taken.
```

## 15. Monitoring

### Actuator endpoints

Served on the API port (5080), no extra setup:

| URL | What it returns |
| --- | --- |
| `/actuator/health` | `{"status":"UP"}` plus details |
| `/actuator/metrics` | list of available metrics (`/actuator/metrics/{name}` for one) |
| `/actuator/prometheus` | all metrics in Prometheus text format |

Only `health`, `info`, `metrics` and `prometheus` are exposed (`management.endpoints.web.exposure.include`). There
is no authentication, which is fine locally but should not be exposed publicly as is.

### Metrics

Besides the standard JVM, system and `http_server_requests_seconds` (latency per endpoint and status) metrics:

| Prometheus name | Type | Meaning |
| --- | --- | --- |
| `tictactoe_games_created_total{mode}` | counter | Games created (`two_player` / `vs_computer`) |
| `tictactoe_moves_played_total{player}` | counter | Accepted moves, including the computer's (`X` / `O`) |
| `tictactoe_games_completed_total{outcome}` | counter | Games that reached a result (`x_win`, `o_win`, `draw`) |
| `tictactoe_undos_total`, `tictactoe_resets_total` | counter | Undo and reset actions |
| `tictactoe_requests_rejected_total{status}` | counter | Rejected requests by HTTP status (400 / 404 / 409) |
| `tictactoe_scoreboard{result}` | gauge | Current scoreboard values; they go down when an undo reverses a result |
| `tictactoe_games_active` | gauge | Game sessions currently held in memory |

Every metric also carries `application="tic-tac-toe-api"`.

### Prometheus

`monitoring/prometheus.yml` scrapes `/actuator/prometheus` every 5 seconds. With Docker installed:

```bash
# 1. start the backend (section 4), then:
cd monitoring
docker compose up -d
```

Open **http://localhost:9090**, then check **Status → Targets** (the `tic-tac-toe-api` job should be `UP`) and try
some queries after playing a few games:

```
tictactoe_scoreboard                                        # current X wins / O wins / draws
sum by (outcome) (tictactoe_games_completed_total)          # results since the app started
rate(tictactoe_moves_played_total[1m])                      # moves per second
sum by (status) (tictactoe_requests_rejected_total)         # rejected moves by status
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[1m])))
```

Without Docker, install Prometheus yourself and use the same file after changing the target to `localhost:5080`.
The target `host.docker.internal:5080` works with Docker Desktop and, through the `extra_hosts` entry in
`docker-compose.yml`, on Linux.

## 16. Security features

- Cross Origin Resource Sharing(CORS)

## 17. Project layout

```
backend/
  pom.xml
  src/main/java/com/example/tictactoe/
    domain/    GameEngine (pure rules), Game, Move, Scoreboard, enums, GameException
    service/   GameService (sessions, undo/reset, scoreboard)
    api/       GameController, Dtos, ApiExceptionHandler, CorsConfig
  src/main/resources/   application.properties, log4j2-spring.xml
  src/test/java/com/example/tictactoe/   GameEngineTest, GameServiceTest, GameApiTest, ActuatorTest
monitoring/
  prometheus.yml, docker-compose.yml   Prometheus scrape config and a one-command Prometheus container
frontend/src/app/
  app.component.ts   state (signals), API calls, layout
  board / scoreboard / move-history components, game-api.service.ts, models.ts
```
