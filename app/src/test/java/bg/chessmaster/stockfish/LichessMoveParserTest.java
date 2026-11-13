package bg.chessmaster.stockfish;

import org.junit.Test;
import static org.junit.Assert.*;

public class LichessMoveParserTest {
    @Test public void readsRealLichessSanMovesAndBuildsPositions() throws Exception {
        // Real NDJSON game exports use SAN moves, not UCI coordinates.
        String san="d4 d5 c4 e6 Nc3 Nf6 Bg5 Be7 e3 O-O Nf3 Nbd7 Bd3 dxc4 Bxc4 c5 O-O";
        LichessMoveParser.Replay game=LichessMoveParser.parse(san);
        assertEquals(17,game.moves.size());
        assertEquals(18,game.positions.size());
        assertEquals("d4",game.san[0]);
        assertEquals("O-O",game.san[9]);
        assertEquals("O-O",game.san[16]);
        assertEquals("d2d4",game.moves.get(0).toString());
    }

    @Test public void readsUciCoordinateMovesForOlderSavedGames() throws Exception {
        LichessMoveParser.Replay game=LichessMoveParser.parse(
            "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6");
        assertEquals(6,game.moves.size());
        assertEquals(7,game.positions.size());
        assertEquals("e4",game.san[0]);
        assertEquals("Bb5",game.san[4]);
    }

    @Test public void rejectsEmptyGames() throws Exception {
        try {
            LichessMoveParser.parse("  ");
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length()>0);
        }
    }
}
