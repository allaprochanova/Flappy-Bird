// flappy.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Runtime.InteropServices;

class FlappyBird
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "yellow" => "\x1b[93m",
            "green" => "\x1b[92m",
            "red" => "\x1b[91m",
            "cyan" => "\x1b[96m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    static string ConfigFile => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".flappy_record.json");

    static int LoadRecord()
    {
        if (!File.Exists(ConfigFile)) return 0;
        string json = File.ReadAllText(ConfigFile);
        var data = JsonSerializer.Deserialize<Dictionary<string,int>>(json);
        return data.GetValueOrDefault("record", 0);
    }

    static void SaveRecord(int record)
    {
        var data = new Dictionary<string,int> { { "record", record } };
        string json = JsonSerializer.Serialize(data);
        File.WriteAllText(ConfigFile, json);
    }

    static void Main(string[] args)
    {
        int speed = 100;
        for (int i=0; i<args.Length; i++)
        {
            if (args[i] == "-s" && i+1 < args.Length)
                speed = int.Parse(args[++i]);
            else if (args[i] == "-h") { Console.WriteLine("Usage: flappy [-s speed_ms]"); return; }
        }

        Console.Clear();
        int height = Console.WindowHeight;
        int width = Console.WindowWidth;
        if (height < 20 || width < 40)
        {
            Console.WriteLine("Terminal too small (min 20x40)");
            return;
        }

        Random rand = new Random();
        int birdY = height / 2;
        int birdX = width / 4;
        float vel = 0;
        const float gravity = 0.5f;
        const float jump = -8f;
        const int pipeW = 5;
        const int pipeGap = 8;
        const int pipeSpacing = 25;
        List<(int x, int top)> pipes = new List<(int, int)>();
        int score = 0;
        int best = LoadRecord();
        bool gameOver = false;
        int frameTime = speed;

        Console.CursorVisible = false;

        while (true)
        {
            if (Console.KeyAvailable)
            {
                var key = Console.ReadKey(true).Key;
                if (key == ConsoleKey.Q) break;
                if (key == ConsoleKey.Spacebar || key == ConsoleKey.UpArrow)
                {
                    if (!gameOver)
                    {
                        vel = jump;
                        Console.Beep();
                    }
                    else
                    {
                        birdY = height / 2;
                        vel = 0;
                        pipes.Clear();
                        score = 0;
                        gameOver = false;
                        continue;
                    }
                }
            }

            if (gameOver)
            {
                Console.Clear();
                string msg = $"💀 Game Over! Score: {score}  Best: {best}";
                Console.SetCursorPosition((width - msg.Length) / 2, height / 2 - 2);
                Console.Write(Colorize(msg, "red"));
                string restart = "Press SPACE to restart";
                Console.SetCursorPosition((width - restart.Length) / 2, height / 2);
                Console.Write(Colorize(restart, "cyan"));
                string quit = "Q - quit";
                Console.SetCursorPosition((width - quit.Length) / 2, height / 2 + 2);
                Console.Write(Colorize(quit, "cyan"));
                continue;
            }

            // Physics
            vel += gravity;
            birdY += (int)vel;

            if (birdY <= 0 || birdY >= height - 1)
            {
                gameOver = true;
                if (score > best) { best = score; SaveRecord(best); }
                continue;
            }

            // Pipes
            if (pipes.Count == 0 || pipes[pipes.Count - 1].x < width - pipeSpacing)
            {
                int top = rand.Next(4, height - pipeGap - 4);
                pipes.Add((width, top));
            }

            // Move pipes
            for (int i = 0; i < pipes.Count; i++)
            {
                var p = pipes[i];
                pipes[i] = (p.x - 2, p.top);
            }

            // Collisions & scoring
            List<int> toRemove = new List<int>();
            for (int i = 0; i < pipes.Count; i++)
            {
                var p = pipes[i];
                if (p.x < birdX + 1 && p.x + pipeW > birdX - 1 &&
                    (birdY < p.top || birdY > p.top + pipeGap))
                {
                    gameOver = true;
                    if (score > best) { best = score; SaveRecord(best); }
                    break;
                }
                if (p.x + pipeW < birdX)
                {
                    score++;
                    toRemove.Add(i);
                }
            }
            if (gameOver) continue;
            for (int i = toRemove.Count - 1; i >= 0; i--)
                pipes.RemoveAt(toRemove[i]);

            // Draw
            Console.Clear();
            // Bird
            Console.SetCursorPosition(birdX, birdY);
            Console.Write(Colorize("@", "yellow"));
            // Pipes
            foreach (var p in pipes)
            {
                int x = p.x, top = p.top;
                for (int y = 0; y < top; y++)
                {
                    if (x > 0 && x < width)
                    {
                        Console.SetCursorPosition(x, y);
                        Console.Write(Colorize("#", "green"));
                        Console.SetCursorPosition(x + pipeW - 1, y);
                        Console.Write(Colorize("#", "green"));
                    }
                }
                for (int i = 0; i < pipeW; i++)
                {
                    if (x + i > 0 && x + i < width)
                    {
                        Console.SetCursorPosition(x + i, top);
                        Console.Write(Colorize("#", "green"));
                        Console.SetCursorPosition(x + i, top + pipeGap);
                        Console.Write(Colorize("#", "green"));
                    }
                }
                for (int y = top + pipeGap; y < height; y++)
                {
                    if (x > 0 && x < width)
                    {
                        Console.SetCursorPosition(x, y);
                        Console.Write(Colorize("#", "green"));
                        Console.SetCursorPosition(x + pipeW - 1, y);
                        Console.Write(Colorize("#", "green"));
                    }
                }
            }
            // Score
            Console.SetCursorPosition(2, 0);
            Console.Write(Colorize($"Score: {score}", "cyan"));
            Console.SetCursorPosition(width / 2 - 4, 0);
            Console.Write(Colorize($"Best: {best}", "cyan"));
            Thread.Sleep(frameTime);
        }
    }
}
