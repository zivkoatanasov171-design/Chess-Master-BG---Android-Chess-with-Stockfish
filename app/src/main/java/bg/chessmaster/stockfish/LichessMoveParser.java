package bg.chessmaster.stockfish;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Lichess /api/games/user NDJSON stores its "moves" in SAN, not UCI.
 * Decode the whole line with chesslib, verify the legal sequence, and cache
 * FEN positions for browsing and offline Stockfish analysis.
 */
public final class LichessMoveParser {
    private LichessMoveParser() { }

    public static final class Replay {
        public final ArrayList<Move> moves;
        public final ArrayList<String> positions;
        public final String[] san;

        Replay(ArrayList<Move> moves, ArrayList<String> positions, String[] san) {
            this.moves = moves;
            this.positions = positions;
            this.san = san;
        }
    }

    public static Replay parse(String movesText) throws Exception {
        if (movesText == null || movesText.trim().isEmpty()) {
            throw new IllegalArgumentException("Тази партия няма записани ходове.");
        }
        String moves = movesText.trim();
        if (moves.length() > 25000) {
            throw new IllegalArgumentException("Партията е твърде дълга за преглед.");
        }

        MoveList decoded = new MoveList();
        // The public Lichess NDJSON export is SAN ("e4 e5 Nf3"),
        // but accept UCI coordinates as well, for compatibility with caches.
        String first = moves.split("\\s+", 2)[0];
        boolean uci = first.toLowerCase(Locale.US).matches("[a-h][1-8][a-h][1-8][qrbn]?");
        if (uci) {
            decoded.loadFromText(moves);
        } else {
            decoded.loadFromSan(moves);
        }
        if (decoded.isEmpty()) {
            throw new IllegalArgumentException("Не са открити разпознаваеми шахматни ходове.");
        }
        if (decoded.size() > 1000) {
            throw new IllegalArgumentException("Партията има твърде много ходове.");
        }

        Board board = new Board();
        ArrayList<Move> replay = new ArrayList<>();
        ArrayList<String> fens = new ArrayList<>();
        fens.add(board.getFen());
        for (Move move : decoded) {
            if (!board.doMove(move)) {
                throw new IllegalArgumentException("Невалиден ход № " + (replay.size() + 1) + ": " + move);
            }
            replay.add(move);
            fens.add(board.getFen());
        }
        return new Replay(replay, fens, decoded.toSanArray());
    }
}
