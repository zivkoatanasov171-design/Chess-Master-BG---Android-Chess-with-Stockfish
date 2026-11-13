package bg.chessmaster.stockfish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds opening families that merit study based on points scored by a single
 * Lichess user. Scores are calculated per colour: win=1, draw=0.5, loss=0.
 * This is descriptive training guidance, not a claim of engine strength.
 */
public final class OpeningWeaknessAnalyzer {
    private OpeningWeaknessAnalyzer() { }

    public static final class Game {
        public final String white, black, result, opening;
        public Game(String white, String black, String result, String opening) {
            this.white=white == null?"":white;
            this.black=black == null?"":black;
            this.result=result == null?"":result;
            this.opening=opening == null?"":opening;
        }
    }

    public static final class Stat {
        public final String name;
        public final boolean asWhite;
        public int games, wins, draws, losses;
        Stat(String name, boolean asWhite) {this.name=name;this.asWhite=asWhite;}
        public double scorePercent() {
            return games == 0?0:(100.0 * (wins + .5 * draws) / games);
        }
        public String describe() {
            return String.format(Locale.US,
                "%s — %.1f%% точки\n%d партии: %d победи · %d ремита · %d загуби",
                name, scorePercent(), games, wins, draws, losses);
        }
    }

    public static final class Report {
        public final List<Stat> white = new ArrayList<>();
        public final List<Stat> black = new ArrayList<>();
        public int whiteGames, blackGames, missingOpenings, skipped;
    }

    /**
     * "Sicilian Defense: Najdorf" and "Sicilian Defense: Dragon" form
     * one broad opening family to avoid misleading one-game outliers.
     */
    public static String openingFamily(String fullName) {
        if(fullName==null)return "";
        String s=fullName.trim();
        int colon=s.indexOf(':');
        if(colon>0)s=s.substring(0,colon);
        return s.trim();
    }

    public static Report analyze(String username, List<Game> games) {
        Report report=new Report();
        if(username==null || username.trim().isEmpty() || games==null)return report;
        Map<String,Stat> whiteStats=new HashMap<>();
        Map<String,Stat> blackStats=new HashMap<>();
        for(Game game:games){
            boolean white=username.equalsIgnoreCase(game.white.trim());
            boolean black=username.equalsIgnoreCase(game.black.trim());
            if(white==black){ report.skipped++; continue; }
            // Ignore unfinished/aborted games or records without a result.
            if(!("1-0".equals(game.result) || "0-1".equals(game.result) ||
                  "1/2-1/2".equals(game.result))){report.skipped++;continue;}
            if(white)report.whiteGames++;else report.blackGames++;
            String family=openingFamily(game.opening);
            if(family.isEmpty() || family.equals("?")){report.missingOpenings++;continue;}
            Map<String,Stat> byColour=white?whiteStats:blackStats;
            String key=family.toLowerCase(Locale.US);
            Stat stat=byColour.get(key);
            if(stat==null){stat=new Stat(family,white);byColour.put(key,stat);}
            stat.games++;
            boolean won=white?"1-0".equals(game.result):"0-1".equals(game.result);
            boolean lost=white?"0-1".equals(game.result):"1-0".equals(game.result);
            if(won)stat.wins++;
            else if(lost)stat.losses++;
            else stat.draws++;
        }
        report.white.addAll(whiteStats.values());
        report.black.addAll(blackStats.values());
        Comparator<Stat> weakestFirst=(a,b)->{
            int compared=Double.compare(a.scorePercent(),b.scorePercent());
            if(compared!=0)return compared;
            compared=Integer.compare(b.games,a.games);
            return compared!=0?compared:a.name.compareToIgnoreCase(b.name);
        };
        Collections.sort(report.white,weakestFirst);
        Collections.sort(report.black,weakestFirst);
        return report;
    }

    /** Prioritize families played at least three times; otherwise report
      * the few available games with a prominent small-sample warning. */
    public static List<Stat> weakest(List<Stat> stats,int limit) {
        List<Stat> candidates=new ArrayList<>();
        for(Stat s:stats)if(s.games>=3)candidates.add(s);
        if(candidates.isEmpty())candidates.addAll(stats);
        if(candidates.size()>limit)return new ArrayList<>(candidates.subList(0,limit));
        return candidates;
    }
}
