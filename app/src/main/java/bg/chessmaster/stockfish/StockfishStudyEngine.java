package bg.chessmaster.stockfish;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reuses a single Stockfish UCI process while screening multiple positions.
 * Independent from the interactive engine so analysis can be cancelled.
 */
public final class StockfishStudyEngine
    implements CriticalMoveAnalyzer.Evaluator, AutoCloseable {

    private static final Pattern SCORE=Pattern.compile("\\bscore\\s+(cp|mate)\\s+(-?\\d+)");
    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final int thinkMs;

    public StockfishStudyEngine(String executablePath,int thinkMs)throws Exception{
        this.thinkMs=Math.max(100,Math.min(1000,thinkMs));
        process=new ProcessBuilder(executablePath).redirectErrorStream(true).start();
        writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader=new BufferedReader(new InputStreamReader(process.getInputStream()));
        try {
            send("uci");readUntil("uciok");
            send("setoption name Threads value 1");
            send("setoption name Hash value 16");
            send("setoption name UCI_LimitStrength value false");
            send("isready");readUntil("readyok");
        } catch(Exception e) {
            process.destroy();
            throw e;
        }
    }

    @Override public Double whiteScore(String fen)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new IOException("Анализът е прекъснат.");
        send("position fen "+fen);
        send("go movetime "+thinkMs);
        String lastScore=null,line;
        while((line=reader.readLine())!=null) {
            if(line.startsWith("info ") && SCORE.matcher(line).find())lastScore=line;
            if(line.startsWith("bestmove "))break;
        }
        if(line==null)throw new IOException("Stockfish приключи неочаквано.");
        if(lastScore==null)throw new IOException("Stockfish не върна оценка.");
        Double result=scoreToWhitePawns(lastScore,fen);
        if(result==null)throw new IOException("Неразпозната оценка от Stockfish.");
        return result;
    }

    public static Double scoreToWhitePawns(String line,String fen){
        if(line==null || fen==null)return null;
        Matcher m=SCORE.matcher(line);
        if(!m.find())return null;
        try{
            int raw=Integer.parseInt(m.group(2));
            double sideToMove=m.group(1).equals("mate")
                ?(raw>0?100.0:raw<0?-100.0:0.0)
                :raw/100.0;
            String[] parts=fen.split("\\s+");
            if(parts.length<2)return null;
            if(parts[1].equals("b"))return -sideToMove;
            if(parts[1].equals("w"))return sideToMove;
        }catch(Exception ignored){}
        return null;
    }

    private void send(String cmd)throws IOException{
        writer.write(cmd);
        writer.newLine();
        writer.flush();
    }
    private void readUntil(String marker)throws IOException{
        String line;
        while((line=reader.readLine())!=null){
            if(line.contains(marker))return;
        }
        throw new IOException("Двигателят Stockfish не стартира.");
    }
    @Override public void close(){
        try{send("quit");}catch(Exception ignored){}
        try{
            if(!process.waitFor(250,TimeUnit.MILLISECONDS))process.destroy();
        }catch(Exception ignored){process.destroy();}
    }
}
