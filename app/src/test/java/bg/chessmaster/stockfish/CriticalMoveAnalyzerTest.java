package bg.chessmaster.stockfish;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CriticalMoveAnalyzerTest {
    private static CriticalMoveAnalyzer.Game g(String id,String white,String black,
             String[] fens,String[] san){
        return new CriticalMoveAnalyzer.Game(id,white,black,"Queen's Gambit",
            Arrays.asList(fens),san);
    }
    private static final CriticalMoveAnalyzer.Progress RUNNING=
        new CriticalMoveAnalyzer.Progress(){
            public boolean cancelled(){return false;}
            public void updated(int d,int t,String g){}
        };

    @Test public void detectsFirstWhiteMoveThatLosesEquality()throws Exception{
        String[] fen={"A0","A1","A2","A3","A4","A5","A6"};
        double[] score={.1,.2,.0,.1,.1,-1.25,-1.4};
        Map<String,Double> m=new HashMap<>();
        for(int i=0;i<fen.length;i++)m.put(fen[i],score[i]);
        CriticalMoveAnalyzer.Game game=g("g1","Me","Opponent",fen,
            new String[]{"d4","d5","c4","e6","Nc3","Nf6"});
        CriticalMoveAnalyzer.Finding f=CriticalMoveAnalyzer.firstCritical(
            game,true,40,m::get,RUNNING);
        assertNotNull(f);
        assertEquals(3,f.fullMove);
    }

    @Test public void countsThirdMoveAndBlackMoveCorrectly()throws Exception{
        String[] fens={"B0","B1","B2","B3","B4","B5","B6"};
        Map<String,Double> m=new HashMap<>();
        for(int i=0;i<6;i++)m.put(fens[i],.05);
        m.put(fens[6],1.35);
        CriticalMoveAnalyzer.Game game=g("black","Opponent","Me",fens,
            new String[]{"e4","c6","d4","d5","Nc3","dxe4"});
        CriticalMoveAnalyzer.Finding f=CriticalMoveAnalyzer.firstCritical(
            game,false,40,m::get,RUNNING);
        assertNotNull(f);
        assertEquals(3,f.fullMove);
        assertEquals(5,f.halfMoveIndex);
        assertTrue(f.before<0);
        assertTrue(f.after<-.90);
    }

    @Test public void doesNotMistakeAlreadyLosingPositionForFirstLoss() throws Exception{
        String[] fens={"C0","C1","C2"};
        CriticalMoveAnalyzer.Game game=g("c","Me","X",fens,new String[]{"e4","e5"});
        Map<String,Double> m=new HashMap<>();
        m.put("C0",-1.75);m.put("C1",-3.15);m.put("C2",-3.6);
        assertNull(CriticalMoveAnalyzer.firstCritical(game,true,10,m::get,RUNNING));
        assertFalse(CriticalMoveAnalyzer.isCritical(-1.75,-3.15,true));
    }

    @Test public void reportsPeakMoveForBothColours() throws Exception{
        Map<String,Double> m=new HashMap<>();
        List<CriticalMoveAnalyzer.Game> games=new ArrayList<>();
        for(int j=0;j<2;j++){
            String[] fens=new String[6];
            for(int i=0;i<fens.length;i++){
                fens[i]="W"+j+"_"+i;
                m.put(fens[i],i==5?-1.3:.0);
            }
            games.add(g("w"+j,"Me","Other",fens,
                new String[]{"d4","d5","c4","e6","Nc3"}));
        }
        CriticalMoveAnalyzer.Report report=CriticalMoveAnalyzer.analyze(
            "Me",games,5,40,m::get,RUNNING);
        assertEquals(2,report.examined);
        assertEquals(2,report.findings.size());
        assertEquals(3,report.peaks(true).get(0).moveNumber);
        assertEquals(2,report.peaks(true).get(0).count);
    }
}
