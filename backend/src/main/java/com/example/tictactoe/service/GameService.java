package com.example.tictactoe.service;

import com.example.tictactoe.api.Dtos.GameStateDto;
import com.example.tictactoe.api.Dtos.MoveDto;
import com.example.tictactoe.api.Dtos.ScoreboardDto;
import com.example.tictactoe.domain.Evaluation;
import com.example.tictactoe.domain.Game;
import com.example.tictactoe.domain.GameEngine;
import com.example.tictactoe.domain.GameException;
import com.example.tictactoe.domain.GameMode;
import com.example.tictactoe.domain.GameStatus;
import com.example.tictactoe.domain.Move;
import com.example.tictactoe.domain.Player;
import com.example.tictactoe.domain.ScoreOutcome;
import com.example.tictactoe.domain.Scoreboard;
import com.example.tictactoe.utility.UtilityMethods;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Owns every game session and the session-level scoreboard (in memory).
 * Every public method is synchronized so game and scoreboard updates stay atomic; that is plenty
 * for a local app.
 *
 * <p>Metrics (Micrometer, exposed to Prometheus):
 * <ul>
 *   <li>{@code tictactoe.games.created{mode}}, {@code tictactoe.moves.played{player}},
 *       {@code tictactoe.undos}, {@code tictactoe.resets}, {@code tictactoe.games.completed{outcome}}: counters</li>
 *   <li>{@code tictactoe.scoreboard{result}} and {@code tictactoe.games.active}: gauges that always
 *       mirror the current scoreboard (including results taken back by undo) and the sessions in memory</li>
 * </ul>
 */
@Service
public class GameService {
    private static final Logger log = LogManager.getLogger(GameService.class);

    // ConcurrentHashMap so the gauge can read the size without taking the service lock.
    private final Map<UUID, Game> games = new ConcurrentHashMap<>();
    private final Scoreboard scoreboard = new Scoreboard();
    private final MeterRegistry metrics;

    public GameService(MeterRegistry metrics) {
        this.metrics = metrics;

        Gauge.builder("tictactoe.games.active", games, Map::size)
                .description("Game sessions currently held in memory")
                .register(metrics);

        for (ScoreOutcome outcome : ScoreOutcome.values()) {
            Gauge.builder("tictactoe.scoreboard", this, service -> service.scoreOf(outcome))
                    .description("Current scoreboard value (draws, X wins, O wins)")
                    .tag("result", outcome.name().toLowerCase())
                    .register(metrics);
        }
    }

    public synchronized GameStateDto create(GameMode mode) {
        Game game = new Game(mode);
        games.put(game.getId(), game);
        metrics.counter("tictactoe.games.created", "mode", mode.name().toLowerCase()).increment();
        log.info("Created game {} in mode {}", game.getId(), mode);
        return toState(game);
    }

    public synchronized GameStateDto get(UUID id) {
        return toState(find(id));
    }

    public synchronized GameStateDto playMove(UUID id, Player player, int row, int column) {
        Game game = find(id);

        if (row < 0 || row >= GameEngine.SIZE || column < 0 || column >= GameEngine.SIZE) {
            throw GameException.badRequest("Row and column must be between 0 and " + (GameEngine.SIZE - 1) + ".");
        }
        if (evaluate(game).status() != GameStatus.IN_PROGRESS) {
            throw GameException.conflict("This game is already finished. Reset the game to play again.");
        }
        if (game.getMode() == GameMode.VS_COMPUTER && player != Player.X) {
            throw GameException.conflict("In computer mode you play X; O is played by the computer.");
        }
        Player expected = nextPlayer(game);
        if (player != expected) {
            throw GameException.conflict("It is " + expected + "'s turn.");
        }
        int cell = row * GameEngine.SIZE + column;
        if (GameEngine.buildBoard(game.getMoves())[cell] != null) {
            throw GameException.conflict("That cell is already taken.");
        }

        addMove(game, new Move(player, cell));

        // The computer replies immediately, but only if the human's move did not end the game.
        if (game.getMode() == GameMode.VS_COMPUTER && evaluate(game).status() == GameStatus.IN_PROGRESS) {
            int reply = GameEngine.chooseComputerMove(GameEngine.buildBoard(game.getMoves()), Player.O);
            addMove(game, new Move(Player.O, reply));
        }

        syncScoreboard(game);
        return toState(game);
    }

    public synchronized GameStateDto undo(UUID id) {
        Game game = find(id);
        List<Move> moves = game.getMoves();
        if (moves.isEmpty()) {
            throw GameException.conflict("There are no moves to undo.");
        }

        // Two Player: remove the latest move.
        // Computer: remove the computer's move together with the human move before it, so it is the
        // human's turn again. If the game ended on the human's move (win/draw) the computer never
        // replied, so only that single move is removed.
        int remove = game.getMode() == GameMode.VS_COMPUTER && moves.size() % 2 == 0 ? 2 : 1;
        for (int i = 0; i < remove; i++) {
            moves.remove(moves.size() - 1);
        }
        metrics.counter("tictactoe.undos").increment();
        log.info("Undo on game {}: removed {} move(s), {} left", id, remove, moves.size());

        syncScoreboard(game); // undoing a finished game takes its result back off the scoreboard
        return toState(game);
    }

    /** Fresh game, same id. The scoreboard is untouched. {@code mode} may be null to keep the current mode. */
    public synchronized GameStateDto reset(UUID id, GameMode mode) {
        Game game = find(id);
        game.getMoves().clear();
        game.setRecordedOutcome(null); // a finished game's result stays on the scoreboard
        if (mode != null) {
            game.setMode(mode);
        }
        metrics.counter("tictactoe.resets").increment();
        log.info("Reset game {} (mode {})", id, game.getMode());
        return toState(game);
    }

    public synchronized ScoreboardDto getScoreboard() {
        return snapshot();
    }

    public synchronized ScoreboardDto resetScoreboard() {
        scoreboard.clear();
        // Forget what finished games contributed, otherwise undoing one would drive a counter negative.
        games.values().forEach(game -> game.setRecordedOutcome(null));
        log.info("Scoreboard reset");
        return snapshot();
    }

    // ---------------------------------------------------------------------------------------------

    private Game find(UUID id) {
        Game game = games.get(id);
        if (game == null) throw GameException.notFound("Game not found.");
        return game;
    }

    private void addMove(Game game, Move move) {
        game.getMoves().add(move);
        metrics.counter("tictactoe.moves.played", "player", move.player().name()).increment();
        log.debug("Game {}: {} played cell {} (move {})",
                game.getId(), move.player(), move.cell(), game.getMoves().size());
    }

    private static Player nextPlayer(Game game) {
        return game.getMoves().size() % 2 == 0 ? Player.X : Player.O;
    }

    private static Evaluation evaluate(Game game) {
        return GameEngine.evaluate(GameEngine.buildBoard(game.getMoves()));
    }

    /**
     * Makes the scoreboard agree with the game's current outcome. It is idempotent, so a completed
     * game is counted exactly once, and undoing a finished game reverts its result.
     */
    private void syncScoreboard(Game game) {
        ScoreOutcome outcome = toOutcome(evaluate(game));
        ScoreOutcome previous = game.getRecordedOutcome();
        if (previous == outcome) return;

        if (previous != null) {
            scoreboard.add(previous, -1);
            log.info("Game {} reopened: {} taken off the scoreboard", game.getId(), previous);
        }
        if (outcome != null) {
            scoreboard.add(outcome, +1);
            metrics.counter("tictactoe.games.completed", "outcome", outcome.name().toLowerCase()).increment();
            log.info("Game {} finished: {} (scoreboard X={} O={} draws={})", game.getId(), outcome,
                    scoreboard.getXWins(), scoreboard.getOWins(), scoreboard.getDraws());
        }
        game.setRecordedOutcome(outcome);
    }

    private double scoreOf(ScoreOutcome outcome) {
        return switch (outcome) {
            case X_WIN -> scoreboard.getXWins();
            case O_WIN -> scoreboard.getOWins();
            case DRAW -> scoreboard.getDraws();
        };
    }

    private static ScoreOutcome toOutcome(Evaluation evaluation) {
        return switch (evaluation.status()) {
            case WON -> evaluation.winner() == Player.X ? ScoreOutcome.X_WIN : ScoreOutcome.O_WIN;
            case DRAW -> ScoreOutcome.DRAW;
            case IN_PROGRESS -> null;
        };
    }

    private ScoreboardDto snapshot() {
        return new ScoreboardDto(scoreboard.getXWins(), scoreboard.getOWins(), scoreboard.getDraws());
    }

    private GameStateDto toState(Game game) {
        Player[] board = GameEngine.buildBoard(game.getMoves());
        Evaluation evaluation = GameEngine.evaluate(board);
        List<Move> moves = game.getMoves();

        List<MoveDto> history = IntStream.range(0, moves.size())
                .mapToObj(i -> new MoveDto(
                        i + 1,
                        moves.get(i).player(),
                        moves.get(i).cell() / GameEngine.SIZE,
                        moves.get(i).cell() % GameEngine.SIZE))
                .toList();

        return new GameStateDto(
                game.getId(),
                board,
                evaluation.status() == GameStatus.IN_PROGRESS ? nextPlayer(game) : null,
                game.getMode(),
                evaluation.status(),
                evaluation.winner(),
                evaluation.winningCells(),
                history,
                snapshot());
    }
}
