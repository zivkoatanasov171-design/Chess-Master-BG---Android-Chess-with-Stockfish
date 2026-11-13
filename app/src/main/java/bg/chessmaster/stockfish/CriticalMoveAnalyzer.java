package bg.chessmaster.stockfish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds the FIRST move by the selected Lichess player that turns an
 * approximately equal position into a clearly inferior position.
 *
 * Engine scores are from White's perspective, not from the side to move.
 * This is a quick, finite-depth screening aid, NOT a proof that a particular
 * opening is faulty. Rerun important positions with deeper manual analysis.
 */
public final class CriticalMoveAnalyzer {
    private CriticalMoveAnalyzer() {}

    public static final double EQUALITY_LIMIT = 0.80;
    public static final double CLEAR_DISADVANTAGE = -0.90;
    public static final double MIN_DROP = 0.85;

    public interface Evaluator {
        /** Pawn score from White's perspective; null if no valid score. */
        Double whiteScore(String fen) throws Exception;
    }
    public interface Progress {
        boolean cancelled();
        void updated(int done, int total, String gameLabel);
    }
    public static final class Game {
        public final String id,white,black,opening;
        public final List<String> positions;
        public final String[] san;
        public Game(String id,String white,String black,String opening,
                    List<String> positions,String[] san){
            this.id=id;this.white=white;this.black=black;
            this.opening=opening == null?"":opening;
            this.positions=positions;this.san=san;
        }
    }
    public static final class Finding {
        public final Game game;
        public final boolean asWhite;
        /** 0-based halfmove index, before incrementing to the next position. */
        public final int halfMoveIndex;
        public final int fullMove;
        public final String played;
        public final double before, after, loss;
        Finding(Game game,boolean asWhite,int halfMoveIndex,String played,
                double before,double after){
            this.game=game;this.asWhite=asWhite;this.halfMoveIndex=halfMoveIndex;
            this.fullMove=halfMoveIndex/2+1;this.played=played;
            this.before=before;this.after=after;this.loss=before-after;
        }
        public String details(){
            return String.format(Locale.US,
                "%s · %d.%s · %s\nПреди: %+.2f → след: %+.2f (загуба %.2f пешки)",
                game.opening.isEmpty()?"Неизвестен дебют":game.opening,
                fullMove,asWhite?"":"..",played,before,after,loss);
        }
    }
    public static final class Frequency {
        public final int moveNumber, count;
        Frequency(int moveNumber,int count){this.moveNumber=moveNumber;this.count=count;}
    }
    public static final class Report {
        public final List<Finding> findings=new ArrayList<>();
        public int examined, failed, selected;
        public int whiteExamined, blackExamined;
        public List<Frequency> peaks(boolean asWhite){
            Map<Integer,Integer> counts=new HashMap<>();
            for(Finding f:findings){
                if(f.asWhite==asWhite){
                    Integer value=counts.get(f.fullMove);
                    counts.put(f.fullMove,value==null?1:value+1);
                }
            }
            List<Frequency> out=new ArrayList<>();
            for(Map.Entry<Integer,Integer> e:counts.entrySet())
                out.add(new Frequency(e.getKey(),e.getValue()));
            Collections.sort(out,(a,b)->{
                int c=Integer.compare(b.count,a.count);
                return c!=0?c:Integer.compare(a.moveNumber,b.moveNumber);
            });
            return out;
        }
    }
    public static boolean isCritical(double beforeWhite,double afterWhite,boolean asWhite){
        double before=asWhite?beforeWhite:-beforeWhite;
        double after=asWhite?afterWhite:-afterWhite;
        return Math.abs(before)<=EQUALITY_LIMIT &&
            after<=CLEAR_DISADVANTAGE && before-after>=MIN_DROP;
    }
    /**
     * Skip opening moves 1-2 when diagnosing repeated opening errors only if
     * the caller asks for it. Here ALL player moves are checked, including
     * critical early theoretical mistakes.
     */
    public static Finding firstCritical(Game game,boolean asWhite,int maxPly,
                                        Evaluator eval,Progress progress)throws Exception{
        int count=Math.min(maxPly,Math.min(game.positions.size()-1,game.san.length));
        Map<String,Double> cache=new HashMap<>();
        for(int ply=0;ply<count;ply++){
            if(progress.cancelled())return null;
            boolean playerMoved=asWhite?ply%2==0:ply%2==1;
            if(!playerMoved)continue;
            Double a=score(game.positions.get(ply),eval,cache);
            Double b=score(game.positions.get(ply+1),eval,cache);
            if(a==null || b==null)continue;
            if(isCritical(a,b,asWhite)){
                return new Finding(game,asWhite,ply,game.san[ply],
                    asWhite?a:-a,asWhite?b:-b);
            }
        }
        return null;
    }
    private static Double score(String fen,Evaluator e,Map<String,Double> cache)throws Exception{
        if(cache.containsKey(fen))return cache.get(fen);
        Double value=e.whiteScore(fen);
        cache.put(fen,value);
        return value;
    }

    /**
     * Analyze recent games, equally capped by colour. Call from a worker.
     * Allow cancellation between engine positions; engine instance remains
     * owned by the caller so the native Stockfish process can be reused.
     */
    public static Report analyze(String username,List<Game> recent,
                                 int perColour,int maxPly,
                                 Evaluator eval,Progress progress){
        Report report=new Report();
        if(recent==null || username==null)return report;
        ArrayList<Game> chosen=new ArrayList<>();
        int whites=0,blacks=0;
        for(Game g:recent){
            if(g==null || g.white==null || g.black==null || g.positions==null || g.san==null)
                continue;
            boolean white=username.equalsIgnoreCase(g.white.trim());
            boolean black=username.equalsIgnoreCase(g.black.trim());
            if(white==black)continue;
            if(white && whites<perColour){chosen.add(g);whites++;}
            if(black && blacks<perColour){chosen.add(g);blacks++;}
            if(whites>=perColour && blacks>=perColour)break;
        }
        report.selected=chosen.size();
        for(Game game:chosen){
            if(progress.cancelled())break;
            boolean asWhite=username.equalsIgnoreCase(game.white.trim());
            try{
                Finding finding=firstCritical(game,asWhite,maxPly,eval,progress);
                if(progress.cancelled())break;
                if(finding!=null)report.findings.add(finding);
                report.examined++;
                if(asWhite)report.whiteExamined++;else report.blackExamined++;
            }catch(Exception ex){
                report.failed++;
            }
            progress.updated(report.examined+report.failed,report.selected,
                game.white+" – "+game.black);
        }
        return report;
    }
}
