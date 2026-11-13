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
import java.io.File; // Avoid chesslib.File / java.io.File name collision.
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

public class MainActivity extends Activity {
    Board board = new Board();
    ArrayList<Move> moves = new ArrayList<>();
    ArrayList<String> positions = new ArrayList<>();
    int cursor = 0, level = 1800, version = 0;
    volatile int evaluationVersion = 0;
    int boardTheme = 0, pieceStyle = 0;
    boolean analyzing = false, thinking = false, showEvaluation = false, showArrow = false, browsingLichess = false;
    String bestMoveArrow = null;
    String currentLibraryUser = "";
    LichessGame selectedLichessGame = null;
    String[] displayedSan = null;
    ArrayList<LichessGame> libraryGames = new ArrayList<>();
    volatile boolean downloadInProgress = false, studyRunning = false;
    CriticalMoveAnalyzer.Report lastCriticalReport = null;
    String lastCriticalUser = "";
    ExecutorService networkWorker = Executors.newSingleThreadExecutor();
    LinearLayout root;
    BoardView boardView;
    EvaluationBarView evaluationBar;
    TextView info, notation, levelText, evaluationText, evaluationButton, arrowButton;
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
        LinearLayout boardRow = new LinearLayout(this);
        boardRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(boardRow,new LinearLayout.LayoutParams(-1,Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(510))));
        boardView = new BoardView(this);
        boardRow.addView(boardView,new LinearLayout.LayoutParams(0,-1,1));
        evaluationBar = new EvaluationBarView(this);
        LinearLayout.LayoutParams barLayout = new LinearLayout.LayoutParams(dp(26),-1);
        barLayout.setMargins(dp(4),0,0,0);
        boardRow.addView(evaluationBar,barLayout);
        evaluationBar.setVisibility(showEvaluation?View.VISIBLE:View.GONE);
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
        LinearLayout libraryRow = new LinearLayout(this);
        root.addView(libraryRow);
        addButton(libraryRow,"Lichess ↓",()->askLichessUser());
        addButton(libraryRow,"Партии",()->showSavedLibraries());
        addButton(libraryRow,"Моя игра",()->returnToMyGame());
        TextView arrowControl = button("Стрелка",()->toggleArrow());
        arrowControl.setTag("arrowControl");
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(0,dp(49),1);
        arrowParams.setMargins(dp(2),dp(5),dp(2),0);
        libraryRow.addView(arrowControl,arrowParams);
        arrowButton=arrowControl;
        updateArrowButton();
        TextView weaknessButton=button("Слаби дебюти с белите и черните",()->showWeakOpenings());
        LinearLayout.LayoutParams weaknessParams=new LinearLayout.LayoutParams(-1,dp(47));
        weaknessParams.setMargins(dp(2),dp(5),dp(2),dp(2));
        root.addView(weaknessButton,weaknessParams);
        TextView criticalButton=button("Stockfish: Къде губя равенството?",()->showCriticalSettings());
        LinearLayout.LayoutParams criticalParams=new LinearLayout.LayoutParams(-1,dp(49));
        criticalParams.setMargins(dp(2),dp(5),dp(2),dp(2));
        root.addView(criticalButton,criticalParams);
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
            .putString("moves",browsingLichess?getPreferences(0).getString("moves",""):serialize()).putInt("level",level)
            .putInt("boardTheme",boardTheme).putInt("pieceStyle",pieceStyle)
            .putBoolean("showEvaluation",showEvaluation)
            .putBoolean("showArrow",showArrow).apply();
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
        evaluationBar.setVisibility(showEvaluation?View.VISIBLE:View.GONE);
        savePreferences();
        refreshEvaluation();
    }
    void updateArrowButton(){
        if(arrowButton!=null)arrowButton.setText(showArrow?"Стрелка ✓":"Стрелка ✕");
    }
    void toggleArrow(){
        showArrow=!showArrow;
        updateArrowButton();
        savePreferences();
        refreshEvaluation();
    }
    void refreshEvaluation() {
        final int request = ++evaluationVersion;
        bestMoveArrow=null;
        if(boardView!=null) boardView.invalidate();
        if(evaluationBar!=null) {
            evaluationBar.whiteFraction=.5f;
            evaluationBar.invalidate();
        }
        if(!showEvaluation && !showArrow) return;
        if(board.isMated()) {
            if(showEvaluation) {
                evaluationText.setText(board.getSideToMove()==Side.WHITE?"Мат за черните":"Мат за белите");
                evaluationBar.whiteFraction=board.getSideToMove()==Side.WHITE?0f:1f;
                evaluationBar.invalidate();
            }
            return;
        }
        if(board.isDraw() || board.isStaleMate()) {
            if(showEvaluation) evaluationText.setText("Реми · 0.00");
            return;
        }
        if(thinking) {
            if(showEvaluation) evaluationText.setText("Оценка: след хода на Stockfish…");
            return;
        }
        final String fen=board.getFen();
        if(showEvaluation) evaluationText.setText("Stockfish изчислява оценката…");
        worker.execute(()->{
            if(request!=evaluationVersion || (!showEvaluation && !showArrow))return;
            final Engine.EvalInfo result=Engine.evaluatePosition(enginePath,fen);
            runOnUiThread(()->{
                if(request!=evaluationVersion || !board.getFen().equals(fen))return;
                if(showArrow)bestMoveArrow=result==null?null:result.bestMove;
                if(showEvaluation){
                    evaluationText.setText(result==null?"Оценката не е налична":result.summary);
                    if(result!=null && result.hasScore){
                        evaluationBar.whiteFraction=(float)(.5+.5*Math.tanh(result.pawns/3.0));
                    }
                    evaluationBar.invalidate();
                }
                boardView.invalidate();
            });
        });
    }
    void render() {
        if(boardView==null)return;
        boardView.invalidate();
        String state = browsingLichess && selectedLichessGame!=null ?
            "Lichess: "+selectedLichessGame.white+" – "+selectedLichessGame.black+
            " ("+selectedLichessGame.result+") • ход "+cursor+"/"+moves.size() :
            board.isMated() ? "Мат! " + (board.getSideToMove()==Side.WHITE?"Stockfish печели":"Ти печелиш") :
            board.isDraw() || board.isStaleMate() ? "Реми" :
            thinking?"Stockfish пресмята…" :
            cursor<moves.size()?"Преглед на партия • ход "+cursor+"/"+moves.size():
            board.getSideToMove()==Side.WHITE?"Ти си с белите • твой ход":"Stockfish е на ход";
        info.setText(state);
        StringBuilder s=new StringBuilder("Ходове: ");
        for(int i=0;i<moves.size();i++) {if(i%2==0)s.append("\n").append(i/2+1).append(". ");s.append(browsingLichess && displayedSan!=null && i<displayedSan.length ? displayedSan[i] : moves.get(i).toString()).append(i==cursor-1?" ◀ ":"  ");}
        notation.setText(s.toString());
        savePreferences();
    }
    String serialize() {StringBuilder s=new StringBuilder();for(Move m:moves)s.append(m.toString()).append(" ");return s.toString().trim();}
    void restore() {
        level=getPreferences(0).getInt("level",1800);
        boardTheme=Math.max(0,Math.min(boardThemes.length-1,getPreferences(0).getInt("boardTheme",0)));
        pieceStyle=Math.max(0,Math.min(pieceStyles.length-1,getPreferences(0).getInt("pieceStyle",0)));
        showEvaluation=getPreferences(0).getBoolean("showEvaluation",false);
        showArrow=getPreferences(0).getBoolean("showArrow",false);
        try {String s=getPreferences(0).getString("moves","");for(String t:s.split(" ")){if(t.isEmpty())continue;
            Move found=null;for(Move m:board.legalMoves())if(m.toString().equals(t)){found=m;break;}
            if(found==null)break;board.doMove(found);moves.add(found);positions.add(board.getFen());}
            cursor=moves.size();
        }catch(Exception ignored){}
    }
    void newGame() {if(browsingLichess)returnToMyGame();version++;thinking=false;analyzing=false;board=new Board();moves.clear();positions.clear();positions.add(board.getFen());cursor=0;boardView.selected=null;render();refreshEvaluation();}
    void step(int dir) {if(thinking)return;int target=Math.max(0,Math.min(moves.size(),cursor+dir));
        if(target==cursor)return;version++;cursor=target;board.loadFromFen(positions.get(cursor));boardView.selected=null;render();refreshEvaluation();}
    void undoTurn() {if(browsingLichess){Toast.makeText(this,"Партията от Lichess е само за преглед",Toast.LENGTH_SHORT).show();return;}if(thinking)return;version++;int n=Math.min(2,cursor);if(n==0)return;
        cursor-=n;while(moves.size()>cursor)moves.remove(moves.size()-1);
        while(positions.size()>cursor+1)positions.remove(positions.size()-1);
        board.loadFromFen(positions.get(cursor));boardView.selected=null;render();refreshEvaluation();}
    void play(Move move) {
        if(browsingLichess || thinking || cursor!=moves.size() || board.getSideToMove()!=Side.WHITE || board.isMated())return;
        if(!board.doMove(move))return;
        moves.add(move);positions.add(board.getFen());cursor=moves.size();boardView.selected=null;render();
        if(!board.isDraw()&&!board.isMated()&&!board.isStaleMate())requestEngineMove();
        else refreshEvaluation();
    }
    void requestEngineMove() {
        if(browsingLichess)return;
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

    static class LichessGame {
        String id, white, black, result, speed, movesText, opening;
        long createdAt;
        LichessGame(JSONObject json){
            id=json.optString("id","");
            JSONObject players=json.optJSONObject("players");
            white=playerName(players,"white");
            black=playerName(players,"black");
            String winner=json.optString("winner","");
            result=winner.equals("white")?"1-0":winner.equals("black")?"0-1":"1/2-1/2";
            speed=json.optString("speed","?");
            movesText=json.optString("moves","").trim();
            createdAt=json.optLong("createdAt",0);
            JSONObject op=json.optJSONObject("opening");
            opening=op==null?"":op.optString("name","");
        }
        static String playerName(JSONObject players,String side){
            JSONObject p=players==null?null:players.optJSONObject(side);
            JSONObject user=p==null?null:p.optJSONObject("user");
            String name=user==null?"":user.optString("name","");
            if(name.isEmpty())name=p==null?"Анонимен":p.optString("name","Анонимен");
            return name;
        }
        String date(){
            return createdAt<=0?"Без дата":
                new java.text.SimpleDateFormat("dd.MM.yyyy",Locale.US).format(new java.util.Date(createdAt));
        }
        String pgnDate(){
            return createdAt<=0?"????.??.??":
                new java.text.SimpleDateFormat("yyyy.MM.dd",Locale.US).format(new java.util.Date(createdAt));
        }
        String label(){
            return date()+" · "+white+" – "+black+" · "+result+" · "+speed;
        }
    }

    void askLichessUser(){
        if(downloadInProgress){
            Toast.makeText(this,"Изтегляне в ход…",Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form=new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20),dp(8),dp(20),0);
        TextView description=new TextView(this);
        description.setText("Публични партии от lichess.org. За изтегляне е необходим интернет.");
        description.setTextColor(Color.DKGRAY);
        form.addView(description);
        EditText nick=new EditText(this);
        nick.setSingleLine(true);
        nick.setHint("Потребителско име в Lichess");
        nick.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        nick.setText(getPreferences(0).getString("lichessLastUser",""));
        form.addView(nick);
        Spinner qty=new Spinner(this);
        String[] amounts={"20 последни партии","50 последни партии","100 последни партии"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,amounts);
        qty.setAdapter(adapter);
        form.addView(qty);
        new AlertDialog.Builder(this).setTitle("Изтегляне от Lichess")
            .setView(form)
            .setPositiveButton("Изтегли",(dialog,which)->{
                String username=nick.getText().toString().trim();
                if(!username.matches("[A-Za-z0-9_-]{2,30}")){
                    new AlertDialog.Builder(this).setTitle("Невалидно потребителско име")
                        .setMessage("Въведи само букви на латиница, цифри, _ или -.")
                        .setPositiveButton("OK",null).show();
                    return;
                }
                int[] numbers={20,50,100};
                downloadLichessGames(username,numbers[qty.getSelectedItemPosition()]);
            }).setNegativeButton("Отказ",null).show();
    }

    File libraryFile(String username){
        String safe=username.toLowerCase(Locale.US);
        return new File(getFilesDir(),"lichess_"+safe+".ndjson");
    }
    void rememberLibrary(String username){
        java.util.LinkedHashSet<String> users=new java.util.LinkedHashSet<>();
        users.add(username);
        String prev=getPreferences(0).getString("lichessUsers","");
        for(String x:prev.split(","))if(!x.isEmpty() && !x.equalsIgnoreCase(username))users.add(x);
        getPreferences(0).edit()
            .putString("lichessLastUser",username)
            .putString("lichessUsers",android.text.TextUtils.join(",",users)).apply();
    }
    ArrayList<LichessGame> parseGames(String text) throws Exception{
        ArrayList<LichessGame> games=new ArrayList<>();
        for(String line:text.split("\n")){
            if(line.trim().isEmpty())continue;
            JSONObject j=new JSONObject(line);
            String variant=j.optString("variant","standard");
            String initialFen=j.optString("initialFen","startpos");
            if(!variant.equalsIgnoreCase("standard") ||
                !(initialFen.equalsIgnoreCase("startpos") || initialFen.isEmpty()))continue;
            LichessGame game=new LichessGame(j);
            if(!game.movesText.isEmpty() && !game.id.isEmpty())games.add(game);
        }
        return games;
    }
    String readLibraryText(String username)throws Exception{
        File file=libraryFile(username);
        if(!file.isFile())throw new IOException("Няма запазени партии за този потребител.");
        if(file.length()>5000000)throw new IOException("Локалната база е твърде голяма.");
        try(FileInputStream in=new FileInputStream(file);
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1){
                out.write(buf,0,n);
                if(out.size()>5000000)throw new IOException("Базата е твърде голяма.");
            }
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    String fetchLichess(String username,int max)throws Exception{
        // Public Lichess endpoint; no account password or access token is required.
        String endpoint="https://lichess.org/api/games/user/"+username+
            "?max="+max+"&moves=true&opening=true&clocks=false&evals=false&finished=true&sort=dateDesc";
        HttpURLConnection connection=(HttpURLConnection)new URL(endpoint).openConnection();
        try{
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept","application/x-ndjson");
            connection.setRequestProperty("User-Agent","ChessMasterBG/1.2 (Android chess study app)");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            int status=connection.getResponseCode();
            if(status==404)throw new IOException("Потребителят не е намерен в Lichess (404).");
            if(status==429)throw new IOException("Lichess ограничава заявките. Опитай по-късно (429).");
            if(status!=200)throw new IOException("Lichess върна HTTP "+status+".");
            try(BufferedReader in=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){
                StringBuilder result=new StringBuilder();
                String line;
                while((line=in.readLine())!=null){
                    if(line.isEmpty())continue;
                    result.append(line).append('\n');
                    if(result.length()>5000000)throw new IOException("Прекалено голям отговор от Lichess.");
                }
                return result.toString();
            }
        }finally{connection.disconnect();}
    }
    void storeLibrary(String username,String raw)throws Exception{
        File target=libraryFile(username);
        File tmp=new File(getFilesDir(),target.getName()+".part");
        try(FileOutputStream out=new FileOutputStream(tmp)){
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if(target.exists() && !target.delete())throw new IOException("Няма достъп за обновяване на файла.");
        if(!tmp.renameTo(target))throw new IOException("Неуспешно запазване на партиите.");
    }
    void downloadLichessGames(String username,int limit){
        if(downloadInProgress)return;
        downloadInProgress=true;
        Toast.makeText(this,"Изтегляне на "+limit+" партии от Lichess…",Toast.LENGTH_LONG).show();
        networkWorker.execute(()->{
            try{
                String raw=fetchLichess(username,limit);
                ArrayList<LichessGame> games=parseGames(raw);
                if(games.isEmpty())throw new IOException("Няма налични стандартни партии с ходове.");
                storeLibrary(username,raw);
                runOnUiThread(()->{
                    downloadInProgress=false;
                    rememberLibrary(username);
                    currentLibraryUser=username;
                    libraryGames=games;
                    Toast.makeText(this,"Запазени "+games.size()+" партии за "+username,Toast.LENGTH_LONG).show();
                    showGamePicker();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    downloadInProgress=false;
                    new AlertDialog.Builder(this).setTitle("Неуспешно изтегляне")
                        .setMessage(e.getMessage()==null?"Провери интернет връзката.":e.getMessage())
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }
    void showSavedLibraries(){
        String all=getPreferences(0).getString("lichessUsers","");
        ArrayList<String> users=new ArrayList<>();
        for(String user:all.split(","))if(!user.isEmpty() && libraryFile(user).isFile())users.add(user);
        if(users.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Запазени партии")
                .setMessage("Все още няма изтеглени партии. Избери „Lichess ↓“ и въведи потребителско име.")
                .setPositiveButton("OK",null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Избери потребител в Lichess")
            .setItems(users.toArray(new String[0]),(d,which)->openSavedLibrary(users.get(which)))
            .setNegativeButton("Отказ",null).show();
    }
    void openSavedLibrary(String username){
        networkWorker.execute(()->{
            try{
                ArrayList<LichessGame> games=parseGames(readLibraryText(username));
                runOnUiThread(()->{
                    currentLibraryUser=username;
                    libraryGames=games;
                    showGamePicker();
                });
            }catch(Exception e){
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Грешка при отваряне")
                    .setMessage(e.getMessage()).setPositiveButton("OK",null).show());
            }
        });
    }
    void showGamePicker(){
        if(libraryGames.isEmpty()){
            Toast.makeText(this,"Няма стандартни партии за преглед.",Toast.LENGTH_LONG).show();
            return;
        }
        String[] titles=new String[libraryGames.size()];
        for(int i=0;i<titles.length;i++)titles[i]=(i+1)+". "+libraryGames.get(i).label();
        new AlertDialog.Builder(this).setTitle("Lichess: "+currentLibraryUser+" · "+titles.length+" партии")
            .setItems(titles,(dialog,index)->openLichessGame(libraryGames.get(index)))
            .setNeutralButton("Слаби дебюти",(dialog,which)->showWeakOpenings())
            .setNegativeButton("Затвори",null).show();
    }
    void showWeakOpenings() {
        if(libraryGames.isEmpty() || currentLibraryUser.isEmpty()){
            new AlertDialog.Builder(this)
                .setTitle("Слаби дебюти")
                .setMessage("Изтегли партии от Lichess или избери запазен потребител чрез бутона „Партии“.")
                .setPositiveButton("Партии",(d,w)->showSavedLibraries())
                .setNegativeButton("Затвори",null).show();
            return;
        }
        ArrayList<OpeningWeaknessAnalyzer.Game> data=new ArrayList<>();
        for(LichessGame game:libraryGames){
            data.add(new OpeningWeaknessAnalyzer.Game(
                game.white,game.black,game.result,game.opening));
        }
        OpeningWeaknessAnalyzer.Report report=
            OpeningWeaknessAnalyzer.analyze(currentLibraryUser,data);
        StringBuilder message=new StringBuilder();
        message.append("Проучване на ").append(libraryGames.size()).append(" изтеглени партии за ")
            .append(currentLibraryUser).append(".\n\n");
        appendWeakSide(message,"С БЕЛИТЕ",report.whiteGames,report.white);
        message.append("\n");
        appendWeakSide(message,"С ЧЕРНИТЕ",report.blackGames,report.black);
        if(report.missingOpenings>0){
            message.append("\nЗа ").append(report.missingOpenings)
                .append(" партии липсва информация за дебюта.\n");
        }
        if(report.skipped>0){
            message.append("\nПропуснати записи без съвпадащ играч или резултат: ")
                .append(report.skipped).append(".\n");
        }
        message.append("\nТова са дебюти за допълнителна подготовка, не непременно дебюти, които трябва да изоставиш.");
        new AlertDialog.Builder(this)
            .setTitle("Дебютни слабости · "+currentLibraryUser)
            .setMessage(message.toString())
            .setPositiveButton("Към партиите",(d,w)->showGamePicker())
            .setNegativeButton("Затвори",null).show();
    }
    void appendWeakSide(StringBuilder output,String title,int total,List<OpeningWeaknessAnalyzer.Stat> stats) {
        output.append(title).append(" · ").append(total).append(" партии\n");
        List<OpeningWeaknessAnalyzer.Stat> weakest=OpeningWeaknessAnalyzer.weakest(stats,3);
        if(weakest.isEmpty()){
            output.append("Няма достатъчно партии с разпознат дебют.\n");
            return;
        }
        boolean small=true;
        for(OpeningWeaknessAnalyzer.Stat st:weakest){
            if(st.games>=3)small=false;
        }
        if(small) output.append("⚠ Малка извадка: под 3 партии на дебют.\n");
        for(int i=0;i<weakest.size();i++){
            OpeningWeaknessAnalyzer.Stat st=weakest.get(i);
            output.append(i+1).append(". ").append(st.describe()).append("\n");
            output.append("   → Препоръка: разгледай тези партии със Stockfish.\n");
        }
    }

    void showCriticalSettings() {
        if(studyRunning){
            Toast.makeText(this,"Stockfish вече анализира избраните партии.",Toast.LENGTH_LONG).show();
            return;
        }
        if(libraryGames.isEmpty() || currentLibraryUser.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Критични ходове със Stockfish")
                .setMessage("Първо изтегли партиите от Lichess или отвори запазената библиотека чрез „Партии“.")
                .setPositiveButton("Избери партии",(d,w)->showSavedLibraries())
                .setNegativeButton("Затвори",null).show();
            return;
        }
        String[] modes={
            "Бърз · до 3 партии с всеки цвят · първите 15 хода",
            "Нормален · до 5 партии с всеки цвят · първите 20 хода",
            "Подробен · до 10 партии с всеки цвят · първите 25 хода"
        };
        final int[] selected={1};
        new AlertDialog.Builder(this).setTitle("Къде губи равенството?")
            .setMessage("Stockfish проверява само ходове на избрания шахматист. Търси първото рязко преминаване от приблизително равна към значително по-лоша позиция. Това е ориентировъчен анализ.")
            .setSingleChoiceItems(modes,1,(d,w)->selected[0]=w)
            .setPositiveButton("Анализирай",(d,w)->{
                int[] maxPerSide={3,5,10};
                int[] maxHalfMoves={30,40,50};
                int[] thinkingMs={140,190,240};
                int i=selected[0];
                runCriticalScan(maxPerSide[i],maxHalfMoves[i],thinkingMs[i]);
            })
            .setNegativeButton("Отказ",null).show();
    }
    void runCriticalScan(int perColour,int maxPly,int thinkMs) {
        if(studyRunning || libraryGames.isEmpty())return;
        studyRunning=true;
        lastCriticalReport=null;
        final String username=currentLibraryUser;
        final ArrayList<LichessGame> games=new ArrayList<>(libraryGames);
        final AtomicBoolean cancelled=new AtomicBoolean(false);
        AlertDialog progress=new AlertDialog.Builder(this)
            .setTitle("Stockfish · критични ходове")
            .setMessage("Подготвяне на партиите…")
            .setNegativeButton("Прекрати",(d,w)->cancelled.set(true))
            .create();
        progress.setCancelable(false);
        progress.show();
        networkWorker.execute(()->{
            try{
                ArrayList<CriticalMoveAnalyzer.Game> playable=new ArrayList<>();
                int white=0,black=0,invalid=0;
                for(LichessGame g:games){
                    if(cancelled.get())break;
                    boolean w=username.equalsIgnoreCase(g.white.trim());
                    boolean b=username.equalsIgnoreCase(g.black.trim());
                    if(w==b || (w && white>=perColour) || (b && black>=perColour))continue;
                    try{
                        LichessMoveParser.Replay positions=LichessMoveParser.parse(g.movesText);
                        playable.add(new CriticalMoveAnalyzer.Game(
                            g.id,g.white,g.black,g.opening,positions.positions,positions.san));
                        if(w)white++;else black++;
                    }catch(Exception ignored){invalid++;}
                    if(white>=perColour && black>=perColour)break;
                }
                if(playable.isEmpty())throw new IOException("Няма подходящи стандартни партии за анализ.");
                final int invalidFinal=invalid;
                CriticalMoveAnalyzer.Report report;
                try(StockfishStudyEngine stockfish=new StockfishStudyEngine(enginePath,thinkMs)){
                    report=CriticalMoveAnalyzer.analyze(username,playable,perColour,maxPly,
                        stockfish,new CriticalMoveAnalyzer.Progress(){
                            @Override public boolean cancelled(){return cancelled.get();}
                            @Override public void updated(int done,int total,String game){
                                runOnUiThread(()->{
                                    if(progress.isShowing())
                                        progress.setMessage("Проверени "+done+" от "+total+
                                            " партии\n"+game);
                                });
                            }
                        });
                }
                runOnUiThread(()->{
                    studyRunning=false;
                    if(progress.isShowing())progress.dismiss();
                    if(cancelled.get()){
                        Toast.makeText(this,"Анализът е прекратен.",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    lastCriticalReport=report;
                    lastCriticalUser=username;
                    showCriticalReport(report,invalidFinal,maxPly);
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    studyRunning=false;
                    if(progress.isShowing())progress.dismiss();
                    if(cancelled.get())return;
                    new AlertDialog.Builder(this).setTitle("Неуспешен Stockfish анализ")
                        .setMessage(ex.getMessage()==null?"Непозната грешка.":ex.getMessage())
                        .setPositiveButton("OK",null).show();
                });
            }
        });
    }
    void showCriticalReport(CriticalMoveAnalyzer.Report report,int invalid,int maxPly){
        StringBuilder s=new StringBuilder();
        s.append("Проверени ").append(report.examined).append(" партии, до ")
            .append(maxPly/2).append("-ия ход.\n");
        s.append("Критерий: позиция в рамките на ±0.80 пешки се влошава до поне −0.90 пешки за играча и спад ≥ 0.85.\n\n");
        appendCriticalSide(s,"С БЕЛИТЕ",true,report);
        s.append("\n");
        appendCriticalSide(s,"С ЧЕРНИТЕ",false,report);
        if(invalid>0 || report.failed>0)
            s.append("\nПропуснати/неуспешни: ").append(invalid+report.failed).append(" партии.\n");
        s.append("\nБързият анализ не доказва грешка в дебюта. За критичните позиции провери с по-дълъг анализ.");
        AlertDialog.Builder dialog=new AlertDialog.Builder(this)
            .setTitle("Критични ходове · "+lastCriticalUser)
            .setMessage(s.toString())
            .setNegativeButton("Затвори",null);
        if(!report.findings.isEmpty()){
            dialog.setPositiveButton("Отвори позициите",(d,w)->showCriticalFindings(report));
        }
        dialog.show();
    }
    void appendCriticalSide(StringBuilder s,String label,boolean asWhite,
                            CriticalMoveAnalyzer.Report report){
        int count=asWhite?report.whiteExamined:report.blackExamined;
        int found=0;
        for(CriticalMoveAnalyzer.Finding f:report.findings)
            if(f.asWhite==asWhite)found++;
        s.append(label).append(": ").append(found).append(" от ")
            .append(count).append(" анализирани партии с критичен ход.\n");
        List<CriticalMoveAnalyzer.Frequency> peaks=report.peaks(asWhite);
        if(peaks.isEmpty()){
            s.append("Няма засечен преход по избрания критерий.\n");
            return;
        }
        s.append("Най-често на ход: ");
        for(int i=0;i<Math.min(4,peaks.size());i++){
            if(i>0)s.append(", ");
            CriticalMoveAnalyzer.Frequency peak=peaks.get(i);
            s.append(peak.moveNumber).append(" (").append(peak.count).append(" партии)");
        }
        s.append("\n");
    }
    void showCriticalFindings(CriticalMoveAnalyzer.Report report){
        if(report.findings.isEmpty())return;
        String[] names=new String[report.findings.size()];
        for(int i=0;i<names.length;i++){
            CriticalMoveAnalyzer.Finding f=report.findings.get(i);
            names[i]=(f.asWhite?"Бели":"Черни")+" · "+f.fullMove+
                (f.asWhite?".":"...")+f.played+" · "+
                String.format(Locale.US,"−%.2f",f.loss)+" пешки\n"+
                OpeningWeaknessAnalyzer.openingFamily(f.game.opening);
        }
        new AlertDialog.Builder(this).setTitle("Избери критична позиция")
            .setItems(names,(d,index)->openCriticalPosition(report.findings.get(index)))
            .setNegativeButton("Затвори",null).show();
    }
    void openCriticalPosition(CriticalMoveAnalyzer.Finding finding){
        if(!lastCriticalUser.equalsIgnoreCase(currentLibraryUser)){
            Toast.makeText(this,"Отвори отново библиотеката на "+lastCriticalUser,Toast.LENGTH_LONG).show();
            return;
        }
        LichessGame selected=null;
        for(LichessGame g:libraryGames)
            if(g.id.equals(finding.game.id)){selected=g;break;}
        if(selected==null){
            Toast.makeText(this,"Партията вече не е в текущата библиотека.",Toast.LENGTH_LONG).show();
            return;
        }
        openLichessGame(selected);
        if(!browsingLichess || selectedLichessGame!=selected)return;
        cursor=Math.min(positions.size()-1,finding.halfMoveIndex+1);
        board.loadFromFen(positions.get(cursor));
        boardView.selected=null;
        render();
        refreshEvaluation();
        Toast.makeText(this,"Критичен ход "+finding.fullMove+
            (finding.asWhite?".":"...")+finding.played+
            " · Натисни „Анализ“ за подробности.",Toast.LENGTH_LONG).show();
    }

    void openLichessGame(LichessGame game){
        try {
            LichessMoveParser.Replay parsed=LichessMoveParser.parse(game.movesText);
            if(!browsingLichess)savePreferences(); // Preserve existing player game.
            Board fresh=new Board();
            ArrayList<Move> replay=parsed.moves;
            ArrayList<String> fens=parsed.positions;
            String[] san=parsed.san;
            version++;evaluationVersion++;
            thinking=false;
            browsingLichess=true;
            displayedSan=san;
            selectedLichessGame=game;
            board=fresh;
            moves=replay;
            positions=fens;
            cursor=0;
            board.loadFromFen(positions.get(0));
            boardView.selected=null;
            render();
            refreshEvaluation();
        }catch(Exception error){
            new AlertDialog.Builder(this).setTitle("Партията не може да се отвори")
                .setMessage(error.getMessage()==null?"Неизвестна грешка при прочитане на ходовете.":error.getMessage())
                .setPositiveButton("OK",null).show();
        }
    }
    void returnToMyGame(){
        if(!browsingLichess)return;
        version++;evaluationVersion++;thinking=false;
        browsingLichess=false;
        selectedLichessGame=null;
        displayedSan=null;
        board=new Board();
        moves=new ArrayList<>();
        positions=new ArrayList<>();
        positions.add(board.getFen());
        restore();
        boardView.selected=null;
        render();
        if(cursor==moves.size() && board.getSideToMove()==Side.BLACK && !board.isMated() && !board.isDraw())
            requestEngineMove();
        else refreshEvaluation();
    }

    void sharePgn() {
        StringBuilder p=new StringBuilder();
        if(browsingLichess && selectedLichessGame!=null){
            LichessGame g=selectedLichessGame;
            p.append("[Event \"Lichess\"]\n[Site \"https://lichess.org/").append(pgnTag(g.id)).append("\"]\n")
             .append("[Date \"").append(g.pgnDate()).append("\"]\n")
             .append("[White \"").append(pgnTag(g.white)).append("\"]\n")
             .append("[Black \"").append(pgnTag(g.black)).append("\"]\n")
             .append("[Result \"").append(g.result).append("\"]\n\n");
        } else {
            p.append("[Event \"Chess Master BG\"]\n[White \"Player\"]\n[Black \"Stockfish 18\"]\n[Result \"*\"]\n\n");
        }
        try {
            MoveList ml=new MoveList();for(Move m:moves)ml.add(m);
            String[] san=ml.toSanArray();
            for(int i=0;i<san.length;i++){if(i%2==0)p.append(i/2+1).append(". ");p.append(san[i]).append(" ");}
        }catch(Exception ex){for(int i=0;i<moves.size();i++)p.append(moves.get(i)).append(" ");}
        p.append(browsingLichess && selectedLichessGame!=null?selectedLichessGame.result:"*");
        Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,p.toString());
        startActivity(Intent.createChooser(share,"Сподели PGN"));
    }
    static String pgnTag(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ");}
    @Override protected void onDestroy(){version++;evaluationVersion++;worker.shutdownNow();networkWorker.shutdownNow();super.onDestroy();}

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
            drawEngineArrow(c,z,ox,oy);
        }
        void drawEngineArrow(Canvas c,int z,float ox,float oy){
            if(!showArrow || bestMoveArrow==null || bestMoveArrow.length()<4)return;
            String u=bestMoveArrow.toLowerCase(Locale.US);
            int sc=u.charAt(0)-'a',sr=8-(u.charAt(1)-'0');
            int tc=u.charAt(2)-'a',tr=8-(u.charAt(3)-'0');
            if(sc<0||sc>=8||tc<0||tc>=8||sr<0||sr>=8||tr<0||tr>=8)return;
            float sx=ox+(sc+.5f)*z,sy=oy+(sr+.5f)*z;
            float tx=ox+(tc+.5f)*z,ty=oy+(tr+.5f)*z;
            float dx=tx-sx,dy=ty-sy,len=(float)Math.hypot(dx,dy);
            if(len<1)return;
            float ux=dx/len,uy=dy/len;
            float tipx=tx-ux*z*.14f,tipy=ty-uy*z*.14f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(z*.13f);
            paint.setColor(0xce16a4c8);
            c.drawLine(sx,sy,tipx-ux*z*.18f,tipy-uy*z*.18f,paint);
            paint.setStyle(Paint.Style.FILL);
            Path arrow=new Path();
            arrow.moveTo(tipx,tipy);
            arrow.lineTo(tipx-ux*z*.33f-uy*z*.2f,tipy-uy*z*.33f+ux*z*.2f);
            arrow.lineTo(tipx-ux*z*.33f+uy*z*.2f,tipy-uy*z*.33f-ux*z*.2f);
            arrow.close();
            c.drawPath(arrow,paint);
        }
        void drawPiece(Canvas c, Piece piece, float x, float y, int z) {
            String n=piece.name();boolean white=n.startsWith("WHITE");
            String type=n.substring(n.indexOf('_')+1);
            int idx=type.equals("PAWN")?0:type.equals("KNIGHT")?1:type.equals("BISHOP")?2:type.equals("ROOK")?3:type.equals("QUEEN")?4:5;
            // Use the solid (black) chess Unicode silhouette for BOTH sides.
            // Render white pieces with an opaque white fill and a dark outline,
            // rather than using the hollow white Unicode glyphs.
            String glyph=symbols.substring(idx+6,idx+7);
            paint.setShadowLayer(0,0,0,Color.TRANSPARENT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setStyle(Paint.Style.FILL);
            if(pieceStyle==0) {
                paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                paint.setTextSize(z*.79f);
                paint.setStrokeWidth(z*.049f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(white?0xff30312d:0xfff0f2e5);
                c.drawText(glyph,x,y+z*.28f,paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(white?0xffffffff:0xff1e2925);
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
            if(browsingLichess || thinking || cursor!=moves.size() || board.getSideToMove()!=Side.WHITE)return true;
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

    class EvaluationBarView extends View {
        Paint barPaint=new Paint(3);
        volatile float whiteFraction=.5f;
        EvaluationBarView(Context context){super(context);}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(), h=getHeight();
            float t=h*(1-Math.max(0,Math.min(1,whiteFraction)));
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(0xff282e31);
            canvas.drawRect(0,0,w,t,barPaint);
            barPaint.setColor(0xfff9f6eb);
            canvas.drawRect(0,t,w,h,barPaint);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(1));
            barPaint.setColor(0xff89938e);
            canvas.drawRect(.5f,.5f,w-.5f,h-.5f,barPaint);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(0xff16a4c8);
            canvas.drawRect(0,t-dp(1),w,t+dp(1),barPaint);
        }
    }

    static class Engine {
        static String ask(String path,String fen,int elo,boolean analysis) {
            return ask(path,fen,elo,analysis,analysis?3000:850);
        }
        static class EvalInfo {
            String summary, bestMove;
            double pawns=0;
            boolean hasScore=false;
        }
        static EvalInfo evaluatePosition(String path,String fen) {
            String result=ask(path,fen,3190,true,1100);
            if(result==null)return null;
            EvalInfo evaluation=new EvalInfo();
            String[] lines=result.split("\n");
            evaluation.summary=lines.length>0?lines[0]:"Оценка: неизвестна";
            try {
                int p=evaluation.summary.lastIndexOf(':');
                if(evaluation.summary.startsWith("Оценка за белите:") && p>=0){
                    evaluation.pawns=Double.parseDouble(evaluation.summary.substring(p+1).trim());
                    evaluation.hasScore=true;
                } else if(evaluation.summary.startsWith("Мат за белите")){
                    evaluation.pawns=100; evaluation.hasScore=true;
                } else if(evaluation.summary.startsWith("Мат за черните")){
                    evaluation.pawns=-100; evaluation.hasScore=true;
                }
            } catch (Exception ignored){}
            if(lines.length>1){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b[a-h][1-8][a-h][1-8][qrbn]?\\b").matcher(lines[1]);
                if(m.find())evaluation.bestMove=m.group(0);
            }
            return evaluation;
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
