// flappy.cpp
#include <curses.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <vector>
#include <json/json.h> // libjsoncpp-dev

using namespace std;

int loadRecord() {
    ifstream f(getenv("HOME") + string("/.flappy_record.json"));
    Json::Value root;
    if (f >> root) return root["record"].asInt();
    return 0;
}

void saveRecord(int record) {
    Json::Value root;
    root["record"] = record;
    ofstream f(getenv("HOME") + string("/.flappy_record.json"));
    f << root.toStyledString();
}

int main(int argc, char* argv[]) {
    int speed = 100;
    for (int i=1; i<argc; ++i) {
        if (string(argv[i]) == "-s" && i+1 < argc) speed = atoi(argv[++i]);
        else if (string(argv[i]) == "-h") { cout << "Usage: flappy [-s speed_ms]\n"; return 0; }
    }

    initscr();
    cbreak();
    noecho();
    curs_set(0);
    nodelay(stdscr, TRUE);
    keypad(stdscr, TRUE);
    start_color();
    init_pair(1, COLOR_YELLOW, COLOR_BLACK);
    init_pair(2, COLOR_GREEN, COLOR_BLACK);
    init_pair(3, COLOR_RED, COLOR_BLACK);
    init_pair(4, COLOR_CYAN, COLOR_BLACK);

    int height, width;
    getmaxyx(stdscr, height, width);
    if (height < 20 || width < 40) {
        endwin();
        cout << "Terminal too small.\n";
        return 1;
    }

    int bird_y = height/2, bird_x = width/4;
    float vel = 0;
    const float gravity = 0.5, jump = -8;
    const int pipeW = 5, pipeGap = 8, pipeSpacing = 25;
    vector<pair<int,int>> pipes; // x, top_y
    int score = 0, best = loadRecord();
    bool gameOver = false;
    int frameTime = speed * 1000;

    while (true) {
        int ch = getch();
        if (ch == 'q' || ch == 'Q') break;
        if (ch == ' ') {
            if (!gameOver) {
                vel = jump;
                // beep
                putchar('\a');
            } else {
                bird_y = height/2; vel = 0; pipes.clear(); score = 0; gameOver = false;
                continue;
            }
        }

        if (gameOver) {
            clear();
            mvprintw(height/2-2, (width-30)/2, "💀 Game Over! Score: %d  Best: %d", score, best);
            mvprintw(height/2, (width-20)/2, "Press SPACE to restart");
            mvprintw(height/2+2, (width-10)/2, "Q - quit");
            refresh();
            continue;
        }

        // Physics
        vel += gravity;
        bird_y += (int)vel;

        // Borders
        if (bird_y <= 0 || bird_y >= height-1) {
            gameOver = true;
            if (score > best) { best = score; saveRecord(best); }
            continue;
        }

        // Pipes
        if (pipes.empty() || pipes.back().first < width - pipeSpacing) {
            int top = rand() % (height - pipeGap - 4) + 2;
            pipes.push_back({width, top});
        }

        // Move pipes
        for (auto &p : pipes) p.first -= 2;

        // Collisions & scoring
        vector<int> toRemove;
        for (int i=0; i<pipes.size(); ++i) {
            int x = pipes[i].first, top = pipes[i].second;
            if (x < bird_x+1 && x+pipeW > bird_x-1 &&
                (bird_y < top || bird_y > top+pipeGap)) {
                gameOver = true;
                if (score > best) { best = score; saveRecord(best); }
                break;
            }
            if (x + pipeW < bird_x) {
                score++;
                toRemove.push_back(i);
            }
        }
        for (int i=toRemove.size()-1; i>=0; --i)
            pipes.erase(pipes.begin() + toRemove[i]);

        // Draw
        clear();
        // Bird
        attron(COLOR_PAIR(1) | A_BOLD);
        mvaddch(bird_y, bird_x, '@');
        attroff(COLOR_PAIR(1) | A_BOLD);
        // Pipes
        attron(COLOR_PAIR(2));
        for (auto &p : pipes) {
            int x = p.first, top = p.second;
            for (int y=0; y<top; ++y) {
                if (x>0 && x<width) { mvaddch(y, x, '#'); mvaddch(y, x+pipeW-1, '#'); }
            }
            for (int i=0; i<pipeW; ++i) {
                if (x+i>0 && x+i<width) {
                    mvaddch(top, x+i, '#');
                    mvaddch(top+pipeGap, x+i, '#');
                }
            }
            for (int y=top+pipeGap; y<height; ++y) {
                if (x>0 && x<width) { mvaddch(y, x, '#'); mvaddch(y, x+pipeW-1, '#'); }
            }
        }
        attroff(COLOR_PAIR(2));
        // Score
        attron(COLOR_PAIR(4));
        mvprintw(0, 2, "Score: %d", score);
        mvprintw(0, width/2-4, "Best: %d", best);
        attroff(COLOR_PAIR(4));
        refresh();

        usleep(frameTime);
    }

    endwin();
    return 0;
}
