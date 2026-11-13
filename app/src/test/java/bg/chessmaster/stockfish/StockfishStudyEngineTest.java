package bg.chessmaster.stockfish;

import org.junit.Test;
import static org.junit.Assert.*;

public class StockfishStudyEngineTest {
    private final String whiteFen="rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final String blackFen="rnbqkbnr/pppppppp/8/8/8/4P3/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
    @Test public void keepsWhitePerspectiveAcrossTurns(){
        assertEquals(.52,StockfishStudyEngine.scoreToWhitePawns(
            "info depth 12 score cp 52 nodes 5000 pv e2e4",whiteFen),.001);
        assertEquals(-.52,StockfishStudyEngine.scoreToWhitePawns(
            "info depth 12 score cp 52 nodes 5000 pv e7e5",blackFen),.001);
        assertEquals(.82,StockfishStudyEngine.scoreToWhitePawns(
            "info depth 12 score cp -82 nodes 5000 pv e7e5",blackFen),.001);
    }
    @Test public void handlesMateAndMissingScores(){
        assertEquals(-100.0,StockfishStudyEngine.scoreToWhitePawns(
            "info depth 12 score mate 3 pv e7e5",blackFen),.001);
        assertNull(StockfishStudyEngine.scoreToWhitePawns("info depth 12 nodes 10",whiteFen));
    }
}
