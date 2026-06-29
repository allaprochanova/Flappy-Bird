// flappy.java
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class flappy {
    private static String configFile = System.getProperty("user.home") + "/.flappy_record.json";

    private static int loadRecord() throws IOException {
        Path path = Paths.get(configFile);
        if (!Files.exists(path)) return 0;
        String json = new String(Files.readAllBytes(path));
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        return obj.get("record").getAsInt();
    }

    private static void saveRecord(int record) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject obj = new JsonObject();
        obj.addProperty("record", record);
        Files.write(Paths.get(configFile), gson.toJson(obj).getBytes());
    }

    public static void main(String[] args) throws Exception {
        int speed = 100;
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-s") && i+1 < args.length) {
                speed = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-h")) {
                System.out.println("Usage: flappy [-s speed_ms]");
                return;
            }
        }

        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        terminal.enterPrivateMode();
        terminal.setCursorVisible(false);
        TerminalSize size = terminal.getTerminalSize();
        int height = size.getRows();
        int width = size.getColumns();
        if (height < 20 || width < 40) {
            System.out.println("Terminal too small (min 20x40)");
            System.exit(1);
        }

        Random rand = new Random();
        int birdY = height / 2;
        int birdX = width / 4;
        double vel = 0;
        final double gravity = 0.5;
        final double jump = -8;
        final int pipeW = 5;
        final int pipeGap = 8;
        final int pipeSpacing = 25;
        List<int[]> pipes = new ArrayList<>();
        int score = 0;
        int best = loadRecord();
        boolean gameOver = false;
        int frameTime = speed;

        TextGraphics tg = terminal.newTextGraphics();

        while (true) {
            KeyStroke key = terminal.pollInput();
            if (key != null) {
                char ch = key.getCharacter() != null ? key.getCharacter() : 0;
                if (ch == 'q' || ch == 'Q') break;
                if (ch == ' ' || key.getKeyType() == KeyStroke.KeyType.ArrowUp) {
                    if (!gameOver) {
                        vel = jump;
                        System.out.print("\007");
                    } else {
                        birdY = height / 2;
                        vel = 0;
                        pipes.clear();
                        score = 0;
                        gameOver = false;
                        continue;
                    }
                }
            }

            if (gameOver) {
                tg.clear();
                String msg = "💀 Game Over! Score: " + score + "  Best: " + best;
                tg.putString((width - msg.length())/2, height/2-2, msg, TextColor.ANSI.RED);
                String restart = "Press SPACE to restart";
                tg.putString((width - restart.length())/2, height/2, restart, TextColor.ANSI.CYAN);
                String quit = "Q - quit";
                tg.putString((width - quit.length())/2, height/2+2, quit, TextColor.ANSI.CYAN);
                terminal.flush();
                continue;
            }

            // Physics
            vel += gravity;
            birdY += (int)vel;

            if (birdY <= 0 || birdY >= height - 1) {
                gameOver = true;
                if (score > best) { best = score; saveRecord(best); }
                continue;
            }

            // Pipes
            if (pipes.isEmpty() || pipes.get(pipes.size()-1)[0] < width - pipeSpacing) {
                int top = rand.nextInt(height - pipeGap - 4) + 2;
                pipes.add(new int[]{width, top});
            }

            // Move pipes
            for (int[] p : pipes) p[0] -= 2;

            // Collisions and scoring
            List<Integer> toRemove = new ArrayList<>();
            for (int i = 0; i < pipes.size(); i++) {
                int[] p = pipes.get(i);
                int x = p[0], top = p[1];
                if (x < birdX+1 && x+pipeW > birdX-1 &&
                    (birdY < top || birdY > top+pipeGap)) {
                    gameOver = true;
                    if (score > best) { best = score; saveRecord(best); }
                    break;
                }
                if (x + pipeW < birdX) {
                    score++;
                    toRemove.add(i);
                }
            }
            if (gameOver) continue;
            for (int i = toRemove.size()-1; i>=0; i--)
                pipes.remove((int)toRemove.get(i));

            // Draw
            tg.clear();
            // Bird
            tg.putString(birdX, birdY, "@", TextColor.ANSI.YELLOW, TextColor.ANSI.DEFAULT);
            // Pipes
            for (int[] p : pipes) {
                int x = p[0], top = p[1];
                for (int y=0; y<top; y++) {
                    if (x>0 && x<width) {
                        tg.putString(x, y, "#", TextColor.ANSI.GREEN);
                        tg.putString(x+pipeW-1, y, "#", TextColor.ANSI.GREEN);
                    }
                }
                for (int i=0; i<pipeW; i++) {
                    if (x+i>0 && x+i<width) {
                        tg.putString(x+i, top, "#", TextColor.ANSI.GREEN);
                        tg.putString(x+i, top+pipeGap, "#", TextColor.ANSI.GREEN);
                    }
                }
                for (int y=top+pipeGap; y<height; y++) {
                    if (x>0 && x<width) {
                        tg.putString(x, y, "#", TextColor.ANSI.GREEN);
                        tg.putString(x+pipeW-1, y, "#", TextColor.ANSI.GREEN);
                    }
                }
            }
            // Score
            tg.putString(2, 0, "Score: " + score, TextColor.ANSI.CYAN);
            tg.putString(width/2-4, 0, "Best: " + best, TextColor.ANSI.CYAN);
            terminal.flush();

            Thread.sleep(frameTime);
        }
        terminal.exitPrivateMode();
        terminal.close();
    }
}
