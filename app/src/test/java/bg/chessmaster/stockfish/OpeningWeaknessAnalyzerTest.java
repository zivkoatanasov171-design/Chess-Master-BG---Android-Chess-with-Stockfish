package bg.chessmaster.stockfish;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;

public class OpeningWeaknessAnalyzerTest {
    @Test public void computesWeakestOpeningsSeparatelyByColour(){
        String user="ChessFan";
        List<OpeningWeaknessAnalyzer.Game> games=Arrays.asList(
            new OpeningWeaknessAnalyzer.Game("chessfan","Opponent","0-1","Queen's Gambit Declined: Exchange Variation"),
            new OpeningWeaknessAnalyzer.Game("ChessFan","Opponent","0-1","Queen's Gambit Declined: Orthodox Defense"),
            new OpeningWeaknessAnalyzer.Game("ChessFan","Opponent","1/2-1/2","Queen's Gambit Declined: Exchange Variation"),
            new OpeningWeaknessAnalyzer.Game("ChessFan","Opponent","1-0","Catalan Opening: Closed Variation"),
            new OpeningWeaknessAnalyzer.Game("ChessFan","Opponent","1-0","Catalan Opening: Open Defense"),
            new OpeningWeaknessAnalyzer.Game("ChessFan","Opponent","1-0","Catalan Opening: Open Defense"),
            new OpeningWeaknessAnalyzer.Game("Opponent","ChessFan","1-0","Sicilian Defense: Najdorf Variation"),
            new OpeningWeaknessAnalyzer.Game("Opponent","ChessFan","1-0","Sicilian Defense: Dragon"),
            new OpeningWeaknessAnalyzer.Game("Opponent","ChessFan","0-1","Sicilian Defense: Najdorf Variation"),
            new OpeningWeaknessAnalyzer.Game("Opponent","ChessFan","0-1","Caro-Kann Defense: Advance Variation"),
            new OpeningWeaknessAnalyzer.Game("Opponent","ChessFan","0-1","Caro-Kann Defense: Classical Variation"),
            new OpeningWeaknessAnalyzer.Game("Opponent","ChessFan","0-1","Caro-Kann Defense: Exchange Variation"),
            new OpeningWeaknessAnalyzer.Game("Other","Unknown","0-1","Sicilian Defense: Najdorf Variation")
        );
        OpeningWeaknessAnalyzer.Report report=OpeningWeaknessAnalyzer.analyze(user,games);
        assertEquals(6,report.whiteGames);
        assertEquals(6,report.blackGames);
        assertEquals(1,report.skipped);
        assertEquals("Queen's Gambit Declined",report.white.get(0).name);
        assertEquals(3,report.white.get(0).games);
        assertEquals(1,report.white.get(0).draws);
        assertEquals(0,report.white.get(0).wins);
        assertEquals(100.0/6,report.white.get(0).scorePercent(),0.01);
        assertEquals("Sicilian Defense",report.black.get(0).name);
        assertEquals(3,report.black.get(0).games);
        assertEquals(1,report.black.get(0).wins);
        assertEquals(100.0/3,report.black.get(0).scorePercent(),0.01);
        assertEquals(100,report.black.get(1).scorePercent(),0.01);
    }

    @Test public void warnsBySmallSampleWhenNothingHasThreeGames(){
        List<OpeningWeaknessAnalyzer.Game> games=Arrays.asList(
            new OpeningWeaknessAnalyzer.Game("User","X","0-1","French Defense: Advance"),
            new OpeningWeaknessAnalyzer.Game("User","X","1-0","Italian Game: Classical"));
        OpeningWeaknessAnalyzer.Report report=OpeningWeaknessAnalyzer.analyze("USER",games);
        assertEquals("French Defense",OpeningWeaknessAnalyzer.weakest(report.white,3).get(0).name);
        assertEquals(2,OpeningWeaknessAnalyzer.weakest(report.white,3).size());
    }
}
