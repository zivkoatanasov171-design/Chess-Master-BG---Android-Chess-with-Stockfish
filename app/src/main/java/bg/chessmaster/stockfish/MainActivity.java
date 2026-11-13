package bg.chessmaster.stockfish;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    Board board = new Board();
    ArrayList<Move> moves = new ArrayList<>();
    ArrayList<String> positions = new ArrayList<>();
    int cursor = 0, level = 1800, version = 0;
    volatile int evaluationVersion = 0;
    int boardTheme = 0, pieceStyle = 0;
    boolean analyzing = false, thinking = false, showEvaluation = false;
    LinearLayout root;
    BoardView boardView;
    TextView info, notation, levelText, evaluationText, evaluationButton;
    final String[] boardThemes = {"Класическа зелена", "Дървесна", "Синя", "Графит"};
    final String[] pieceStyles = {"Класически фигури", "Минималистични фигури", "Контрастни фигури"};
    final int[][] boardColors = {
        {0xffefe8d4, 0xff71957f},
        {0xfff4d6ab, 0xffb37c51},
        {0xffe0edf3, 0xff719ab5},
        {0xffd9dad6, 0xff77817c}
    };
    ExecutorService worker = Executors.newSingleThreadExecutor();
    String enginePath;
    final String[] strengthLabels = {"Начинаещ · 1320", "Клубен · 1500", "Напреднал · 1800", "Силен · 2100", "Майстор · 2400", "Максимална · 3190"};
    final int[] strengths = {1320,1500,1800,2100,2400,3190};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        enginePath = getApplicationInfo().nativeLibraryDir + "/libstockfish.so";
        positions.add(board.getFen());
        restore();
        createUI();
        render();
        if(cursor==moves.size() && board.getSideToMove()==Side.BLACK) requestEngineMove();
        else refreshEvaluation();
    }
    int dp(float d) {return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    TextView button(String label, Runnable action) {
        TextView t = new TextView(this);
        t.setText(label); t.setTextSize(14); t.setGravity(Gravity.CENTER); t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.rgb(49,75,92)); t.setPadding(dp(9),dp(11),dp(9),dp(11));
        t.setOnClickListener(v -> action.run()); return t;
    }
    void createUI() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(12),dp(10),dp(8)); root.setBackgroundColor(Color.rgb(241,244,239));
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("♟ CHESS MASTER BG"); title.setTextSize(23);
        title.setTextColor(Color.rgb(23,48,55)); title.setGravity(Gravity.CENTER);
        title.setPadding(0,0,0,dp(8)); root.addView(title);
        info = new TextView(this); info.setTextSize(15); info.setGravity(Gravity.CENTER);
        info.setTextColor(Color.DKGRAY); root.addView(info);
        boardView = new BoardView(this);
        root.addView(boardView,new LinearLayout.LayoutParams(-1, Math.min(getResources().getDisplayMetrics().widthPixels-dp(20), dp(510))));
        levelText = new TextView(this); levelText.setTextSize(15); levelText.setTextColor(Color.BLACK);
        levelText.setPadding(dp(4),dp(9),0,dp(5)); root.addView(levelText);
        SeekBar seek = new SeekBar(this); seek.setMax(strengths.length-1);
        int chosen=0;for(int i=0;i<strengths.length;i++)if(strengths[i]==level)chosen=i;
        seek.setProgress(chosen);
        levelText.setText("Сила на Stockfish: "+strengthLabels[chosen]);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){level=strengths[p];levelText.setText("Сила на Stockfish: "+strengthLabels[p]);if(user)savePreferences();}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        root.addView(seek);
        LinearLayout a = new LinearLayout(this); a.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(a);
        addButton(a,"Нова",()->new AlertDialog.Builder(this).setTitle("Нова партия?").setPositiveButton("Да",(d,w)->newGame()).setNegativeButton("Не",null).show());
        addButton(a,"Назад",()->step(-1));
        addButton(a,"Напред",()->step(1));
        LinearLayout b = new LinearLayout(this);root.addView(b);
        addButton(b,"Отмени",()->undoTurn());
        addButton(b,"Анализ",()->analyze());
        addButton(b,"PGN ↗",()->sharePgn());
        LinearLayout options = new LinearLayout(this); options.setOrientation(LinearLayout.HORIZONTAL); root.addView(options);
        addButton(options, "Дъска ▾", () -> selectBoardTheme());
        addButton(options, "Фигури ▾", () -> selectPieceStyle());
        evaluationButton = button("", () -> toggleEvaluation());
        LinearLayout.LayoutParams evParams=new LinearLayout.LayoutParams(0,dp(49),1);
        evParams.setMargins(dp(2),dp(5),dp(2),0);
        options.addView(evaluationButton, evParams);
        updateEvaluationButton();
        evaluationText=new TextView(this);
        evaluationText.setTextSize(16);
        evaluationText.setTextColor(Color.rgb(26,56,65));
        evaluationText.setGravity(Gravity.CENTER);
        evaluationText.setPadding(dp(4),dp(11),dp(4),dp(8));
        evaluationText.setVisibility(showEvaluation?View.VISIBLE:View.GONE);
        root.addView(evaluationText);
        notation=new TextView(this); notation.setTextColor(Color.rgb(28,43,52)); notation.setTextSize(15);
        notation.setPadding(dp(6),dp(12),dp(6),dp(12));root.addView(notation);
        TextView foot = new TextView(this);foot.setText("Игра срещу Stockfish 18 · без интернет · докосни фигура и после поле");
        foot.setTextSize(12);foot.setTextColor(Color.GRAY);root.addView(foot);
    }
    void addButton(LinearLayout row,String label,Runnable action) {
        TextView b=button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(49),1);
        p.setMargins(dp(2),dp(5),dp(2),0);row.addView(b,p);
    }
    void savePreferences() {
        getPreferences(0).edit()
            .putString("moves",serialize()).putInt("level",level)
            .putInt("boardTheme",boardTheme).putInt("pieceStyle",pieceStyle)
            .putBoolean("showEvaluation",showEvaluation).apply();
    }
    void selectBoardTheme() {
        new AlertDialog.Builder(this).setTitle("Избери дизайн на дъската")
            .setSingleChoiceItems(boardThemes,boardTheme,(d,which)->{
                boardTheme=which;boardView.invalidate();savePreferences();d.dismiss();
            }).setNegativeButton("Отказ",null).show();
    }
    void selectPieceStyle() {
        new AlertDialog.Builder(this).setTitle("Избери дизайн на фигурите")
            .setSingleChoiceItems(pieceStyles,pieceStyle,(d,which)->{
                pieceStyle=which;boardView.invalidate();savePreferences();d.dismiss();
            }).setNegativeButton("Отказ",null).show();
    }
    void updateEvaluationButton() {
        if(evaluationButton!=null) evaluationButton.setText(showEvaluation?"Оценка: ВКЛ":"Оценка: ИЗКЛ");
    }
    void toggleEvaluation() {
        showEvaluation=!showEvaluation;
        updateEvaluationButton();
        evaluationText.setVisibility(showEvaluation?View.VISIBLE:View.GONE);
        savePreferences();
        refreshEvaluation();
    }
    void refreshEvaluation() {
        final int request = ++evaluationVersion;
        if(!showEvaluation || evaluationText==null)return;
        if(board.isMated()) {
            evaluationText.setText(board.getSideToMove()==Side.WHITE?"Мат за черните":"Мат за белите");
            return;
        }
        if(board.isDraw() || board.isStaleMate()) {
            evaluationText.setText("Реми · 0.00");
            return;
        }
        if(thinking) {
            evaluationText.setText("Оценка: след хода на Stockfish…");
            return;
        }
        final String fen=board.getFen();
        evaluationText.setText("Stockfish изчислява оценката…");
        worker.execute(()->{
            if(request!=evaluationVersion || !showEvaluation)return;
            final String result=Engine.evaluate(enginePath,fen);
            runOnUiThread(()->{
                if(request!=evaluationVersion || !showEvaluation || !board.getFen().equals(fen))return;
                evaluationText.setText(result==null?"Оценката не е налична":result);
            });
        });
    }
    void render() {
        if(boardView==null)return;
        boardView.invalidate();
        String state = board.isMated() ? "Мат! " + (board.getSideToMove()==Side.WHITE?"Stockfish печели":"Ти печелиш") :
            board.isDraw() || board.isStaleMate() ? "Реми" :
            thinking?"Stockfish пресмята…" :
            cursor<moves.size()?"Преглед на партия • ход "+cursor+"/"+moves.size():
            board.getSideToMove()==Side.WHITE?"Ти си с белите • твой ход":"Stockfish е на ход";
        info.setText(state);
        StringBuilder s=new StringBuilder("Ходове: ");
        for(int i=0;i<moves.size();i++) {if(i%2==0)s.append("\n").append(i/2+1).append(". ");s.append(moves.get(i).toString()).append(i==cursor-1?" ◀ ":"  ");}
        notation.setText(s.toString());
        savePreferences();
    }
    String serialize() {StringBuilder s=new StringBuilder();for(Move m:moves)s.append(m.toString()).append(" ");return s.toString().trim();}
    void restore() {
        level=getPreferences(0).getInt("level",1800);
        boardTheme=Math.max(0,Math.min(boardThemes.length-1,getPreferences(0).getInt("boardTheme",0)));
        pieceStyle=Math.max(0,Math.min(pieceStyles.length-1,getPreferences(0).getInt("pieceStyle",0)));
        showEvaluation=getPreferences(0).getBoolean("showEvaluation",false);
        try {String s=getPreferences(0).getString("moves","");for(String t:s.split(" ")){if(t.isEmpty())continue;
            Move found=null;for(Move m:board.legalMoves())if(m.toString().equals(t)){found=m;break;}
            if(found==null)break;board.doMove(found);moves.add(found);positions.add(board.getFen());}
            cursor=moves.size();
        }catch(Exception ignored){}
    }
    void newGame() {version++;thinking=false;analyzing=false;board=new Board();moves.clear();positions.clear();positions.add(board.getFen());cursor=0;boardView.selected=null;render();refreshEvaluation();}
    void step(int dir) {if(thinking)return;int target=Math.max(0,Math.min(moves.size(),cursor+dir));
        if(target==cursor)return;version++;cursor=target;board.loadFromFen(positions.get(cursor));boardView.selected=null;render();refreshEvaluation();}
    void undoTurn() {if(thinking)return;version++;int n=Math.min(2,cursor);if(n==0)return;
        cursor-=n;while(moves.size()>cursor)moves.remove(moves.size()-1);
        while(positions.size()>cursor+1)positions.remove(positions.size()-1);
        board.loadFromFen(positions.get(cursor));boardView.selected=null;render();refreshEvaluation();}
    void play(Move move) {
        if(thinking || cursor!=moves.size() || board.getSideToMove()!=Side.WHITE || board.isMated())return;
        if(!board.doMove(move))return;
        moves.add(move);positions.add(board.getFen());cursor=moves.size();boardView.selected=null;render();
        if(!board.isDraw()&&!board.isMated()&&!board.isStaleMate())requestEngineMove();
        else refreshEvaluation();
    }
    void requestEngineMove() {
        if(thinking)return;thinking=true;int stamp=++version;String fen=board.getFen();int elo=level;render();refreshEvaluation();
        worker.execute(()->{
            String best=Engine.ask(enginePath,fen,elo,false);
            runOnUiThread(()->{if(stamp!=version)return;thinking=false;
                if(best!=null)for(Move m:board.legalMoves())if(m.toString().equals(best)){
                    board.doMove(m);moves.add(m);positions.add(board.getFen());cursor=moves.size();break;}
                if(best==null)Toast.makeText(this,"Двигателят не стартира. Проверете APK.",Toast.LENGTH_LONG).show();
                render();
                refreshEvaluation();
            });
        });
    }
    void analyze() {
        if(thinking)return;int stamp=version;String fen=board.getFen();info.setText("Анализ на позицията…");
        worker.execute(()->{
            String result=Engine.ask(enginePath,fen,level,true);
            runOnUiThread(()->{if(stamp!=version)return;
                new AlertDialog.Builder(this).setTitle("Анализ на Stockfish")
                    .setMessage(result==null?"Двигателят не е достъпен":result)
                    .setPositiveButton("OK",null).show();render();});
        });
    }
    void sharePgn() {
        StringBuilder p=new StringBuilder("[Event \"Chess Master BG\"]\n[White \"Player\"]\n[Black \"Stockfish 18\"]\n[Result \"*\"]\n\n");
        try {
            MoveList ml=new MoveList();for(Move m:moves)ml.add(m);
            String[] san=ml.toSanArray();
            for(int i=0;i<san.length;i++){if(i%2==0)p.append(i/2+1).append(". ");p.append(san[i]).append(" ");}
        }catch(Exception ex){for(int i=0;i<moves.size();i++)p.append(moves.get(i)).append(" ");}
        p.append("*");
        Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,p.toString());
        startActivity(Intent.createChooser(share,"Сподели PGN"));
    }
    @Override protected void onDestroy(){version++;evaluationVersion++;worker.shutdownNow();super.onDestroy();}

    class BoardView extends View {
        Paint paint=new Paint(3); Square selected=null;
        String symbols="♙♘♗♖♕♔♟♞♝♜♛♚";
        BoardView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        int squareSize(){return getWidth()/8;}
        Square squareAt(int col,int row){return Square.valueOf((""+(char)('A'+col)+(8-row)));}
        @Override protected void onDraw(Canvas c) {
            int z=Math.min(getWidth(),getHeight())/8;if(z==0)return;
            float ox=(getWidth()-8*z)/2f,oy=(getHeight()-8*z)/2f;
            for(int row=0;row<8;row++)for(int col=0;col<8;col++) {
                Square sq=squareAt(col,row);
                boolean light=(row+col)%2==0;
                paint.setColor(boardColors[boardTheme][light?0:1]);
                c.drawRect(ox+col*z,oy+row*z,ox+(col+1)*z,oy+(row+1)*z,paint);
                if(sq==selected){paint.setColor(0x88F5D448);c.drawRect(ox+col*z,oy+row*z,ox+(col+1)*z,oy+(row+1)*z,paint);}
                Piece piece=board.getPiece(sq);
                if(piece!=Piece.NONE) drawPiece(c,piece,ox+(col+.5f)*z,oy+(row+.5f)*z,z);
                if(col==0){paint.setTextSize(z*.18f);paint.setColor(light?0xff56715e:0xfff7f3e7);paint.setTextAlign(Paint.Align.LEFT);c.drawText(""+(8-row),ox+col*z+3,oy+row*z+z*.2f,paint);}
                if(row==7){paint.setTextSize(z*.18f);paint.setColor(light?0xff56715e:0xfff7f3e7);paint.setTextAlign(Paint.Align.RIGHT);c.drawText(""+(char)('a'+col),ox+(col+1)*z-3,oy+(row+1)*z-4,paint);}
            }
        }
        void drawPiece(Canvas c, Piece piece, float x, float y, int z) {
            String n=piece.name();boolean white=n.startsWith("WHITE");
            String type=n.substring(n.indexOf('_')+1);
            int idx=type.equals("PAWN")?0:type.equals("KNIGHT")?1:type.equals("BISHOP")?2:type.equals("ROOK")?3:type.equals("QUEEN")?4:5;
            String glyph=symbols.substring(idx+(white?0:6),idx+(white?0:6)+1);
            paint.setShadowLayer(0,0,0,Color.TRANSPARENT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            if(pieceStyle==0) {
                paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                paint.setTextSize(z*.79f);
                paint.setStrokeWidth(z*.035f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(white?0xff30312d:0xfff0f2e5);
                c.drawText(glyph,x,y+z*.28f,paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(white?Color.WHITE:0xff1e2925);
                c.drawText(glyph,x,y+z*.28f,paint);
                return;
            }
            // Two genuinely distinct alternate looks, drawn locally without external assets.
            paint.setColor(white?0xfffcfaf1:0xff27343a);
            c.drawCircle(x,y,z*.38f,paint);
            paint.setColor(white?0xff354750:0xffe4ebef);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(z*.045f);
            c.drawCircle(x,y,z*.38f,paint);
            paint.setStyle(Paint.Style.FILL);
            if(pieceStyle==1) {
                String labels="PNBRQK";
                paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));
                paint.setTextSize(z*.43f);
                paint.setColor(white?0xff273941:0xfff5f1df);
                c.drawText(""+labels.charAt(idx),x,y+z*.16f,paint);
            } else {
                paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                paint.setTextSize(z*.62f);
                paint.setColor(white?0xff293d4b:0xfffaf7eb);
                c.drawText(glyph,x,y+z*.22f,paint);
            }
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            if(thinking || cursor!=moves.size() || board.getSideToMove()!=Side.WHITE)return true;
            int z=Math.min(getWidth(),getHeight())/8;if(z==0)return true;
            int col=(int)((e.getX()-(getWidth()-8*z)/2f)/z),row=(int)((e.getY()-(getHeight()-8*z)/2f)/z);
            if(col<0||col>7||row<0||row>7)return true;
            Square target=squareAt(col,row);
            if(selected!=null) {
                Move found=null;
                for(Move m:board.legalMoves())if(m.getFrom()==selected && m.getTo()==target) {
                    if(found==null || m.getPromotion()==Piece.WHITE_QUEEN)found=m;
                }
                if(found!=null){play(found);return true;}
            }
            Piece piece=board.getPiece(target);
            selected=piece!=Piece.NONE && piece.name().startsWith("WHITE")?target:null;
            invalidate();return true;
        }
    }

    static class Engine {
        static String ask(String path,String fen,int elo,boolean analysis) {
            return ask(path,fen,elo,analysis,analysis?3000:850);
        }
        static String evaluate(String path,String fen) {
            String analysis = ask(path,fen,3190,true,1000);
            return analysis==null?null:analysis.split("\\n",2)[0];
        }
        static String ask(String path,String fen,int elo,boolean analysis,int timeMs) {
            java.lang.Process process=null;
            try {
                process=new ProcessBuilder(path).redirectErrorStream(true).start();
                BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedWriter w=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                send(w,"uci");readUntil(r,"uciok");
                send(w,"setoption name Threads value 2");
                send(w,"setoption name Hash value 32");
                send(w,"setoption name UCI_LimitStrength value "+(analysis?"false":"true"));
                if(!analysis)send(w,"setoption name UCI_Elo value "+elo);
                send(w,"isready");readUntil(r,"readyok");
                send(w,"position fen "+fen);
                send(w,"go movetime "+timeMs);
                String best=null,lastInfo="";String line;
                while((line=r.readLine())!=null) {
                    if(line.startsWith("info ")&&line.contains(" score ")&&line.contains(" pv "))lastInfo=line;
                    if(line.startsWith("bestmove ")) {String[] v=line.split(" ");if(v.length>1)best=v[1];break;}
                }
                if(!analysis)return best;
                String evaluation="Оценка: неизвестна",variation="Най-добър ход: "+best;
                int whitePerspective=fen.split(" ")[1].equals("w")?1:-1;
                String[] parts=lastInfo.split(" ");
                for(int i=0;i<parts.length-2;i++)if(parts[i].equals("score")){
                    if(parts[i+1].equals("cp")){try{
                        double value=whitePerspective*Integer.parseInt(parts[i+2])/100.0;
                        evaluation="Оценка за белите: "+String.format(Locale.US,"%+.2f",value);
                    }catch(Exception ignored){}}
                    if(parts[i+1].equals("mate")){try{
                        int mate=whitePerspective*Integer.parseInt(parts[i+2]);
                        evaluation="Мат за "+(mate>0?"белите":"черните")+" след "+Math.abs(mate)+" хода";
                    }catch(Exception ignored){}}

                }
                int pv=lastInfo.indexOf(" pv ");if(pv!=-1)variation="Вариант (UCI): "+lastInfo.substring(pv+4);
                return evaluation+"\n"+variation+"\n\nАнализ: 3 секунди";
            }catch(Exception e){return null;}
            finally {if(process!=null){try{send(new BufferedWriter(new OutputStreamWriter(process.getOutputStream())),"quit");}catch(Exception ignored){}process.destroy();}}
        }
        static void send(BufferedWriter w,String cmd)throws IOException{w.write(cmd);w.newLine();w.flush();}
        static void readUntil(BufferedReader r,String token)throws IOException{String x;while((x=r.readLine())!=null)if(x.contains(token))return;throw new IOException("UCI stopped");}
    }
}
