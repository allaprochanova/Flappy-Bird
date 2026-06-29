// flappy.go
package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"os/exec"
	"runtime"
	"strconv"
	"time"
	"github.com/nsf/termbox-go" // go get github.com/nsf/termbox-go
)

const (
	recordFile = ".flappy_record.json"
)

type Record struct {
	Best int `json:"record"`
}

func loadRecord() int {
	f, err := os.Open(recordFile)
	if err != nil {
		return 0
	}
	defer f.Close()
	var r Record
	json.NewDecoder(f).Decode(&r)
	return r.Best
}

func saveRecord(best int) {
	r := Record{Best: best}
	f, _ := os.Create(recordFile)
	defer f.Close()
	json.NewEncoder(f).Encode(r)
}

func main() {
	speed := 100
	if len(os.Args) > 2 && os.Args[1] == "-s" {
		s, _ := strconv.Atoi(os.Args[2])
		if s > 0 {
			speed = s
		}
	}

	err := termbox.Init()
	if err != nil {
		fmt.Println("termbox init failed:", err)
		return
	}
	defer termbox.Close()

	termbox.SetInputMode(termbox.InputEsc)
	w, h := termbox.Size()
	if h < 20 || w < 40 {
		fmt.Println("Terminal too small")
		return
	}

	rand.Seed(time.Now().UnixNano())
	birdY := h / 2
	birdX := w / 4
	vel := 0.0
	const gravity = 0.5
	const jump = -8
	const pipeW = 5
	const pipeGap = 8
	const pipeSpacing = 25
	type pipe struct{ x, top int }
	pipes := []pipe{}
	score := 0
	best := loadRecord()
	gameOver := false
	frame := time.Duration(speed) * time.Millisecond

	for {
		switch ev := termbox.PollEvent(); ev.Type {
		case termbox.EventKey:
			if ev.Key == termbox.KeyEsc || ev.Key == termbox.KeyCtrlC || ev.Ch == 'q' {
				return
			}
			if ev.Ch == ' ' || ev.Key == termbox.KeyArrowUp {
				if !gameOver {
					vel = jump
					// beep
					fmt.Print("\a")
				} else {
					birdY = h / 2
					vel = 0
					pipes = pipes[:0]
					score = 0
					gameOver = false
					continue
				}
			}
		default:
			// Игровой цикл с обновлением раз в frame
			if gameOver {
				termbox.Clear(termbox.ColorDefault, termbox.ColorDefault)
				msg := fmt.Sprintf("💀 Game Over! Score: %d  Best: %d", score, best)
				tbprint(w/2-len(msg)/2, h/2-2, termbox.ColorRed, termbox.ColorDefault, msg)
				tbprint(w/2-10, h/2, termbox.ColorCyan, termbox.ColorDefault, "Press SPACE to restart")
				tbprint(w/2-5, h/2+2, termbox.ColorCyan, termbox.ColorDefault, "Q - quit")
				termbox.Flush()
				continue
			}

			// Physics
			vel += gravity
			birdY += int(vel)

			if birdY <= 0 || birdY >= h-1 {
				gameOver = true
				if score > best {
					best = score
					saveRecord(best)
				}
				continue
			}

			// Pipes
			if len(pipes) == 0 || pipes[len(pipes)-1].x < w-pipeSpacing {
				top := rand.Intn(h-pipeGap-4) + 2
				pipes = append(pipes, pipe{w, top})
			}

			// Move pipes
			for i := range pipes {
				pipes[i].x -= 2
			}

			// Collisions & scoring
			newPipes := []pipe{}
			for _, p := range pipes {
				if p.x < birdX+1 && p.x+pipeW > birdX-1 &&
					(birdY < p.top || birdY > p.top+pipeGap) {
					gameOver = true
					if score > best {
						best = score
						saveRecord(best)
					}
					break
				}
				if p.x+pipeW < birdX {
					score++
				} else {
					newPipes = append(newPipes, p)
				}
			}
			if gameOver {
				continue
			}
			pipes = newPipes

			// Draw
			termbox.Clear(termbox.ColorDefault, termbox.ColorDefault)
			// Bird
			termbox.SetCell(birdX, birdY, '@', termbox.ColorYellow|termbox.AttrBold, termbox.ColorDefault)
			// Pipes
			for _, p := range pipes {
				for y := 0; y < p.top; y++ {
					if p.x > 0 && p.x < w {
						termbox.SetCell(p.x, y, '#', termbox.ColorGreen, termbox.ColorDefault)
						termbox.SetCell(p.x+pipeW-1, y, '#', termbox.ColorGreen, termbox.ColorDefault)
					}
				}
				for i := 0; i < pipeW; i++ {
					if p.x+i > 0 && p.x+i < w {
						termbox.SetCell(p.x+i, p.top, '#', termbox.ColorGreen, termbox.ColorDefault)
						termbox.SetCell(p.x+i, p.top+pipeGap, '#', termbox.ColorGreen, termbox.ColorDefault)
					}
				}
				for y := p.top + pipeGap; y < h; y++ {
					if p.x > 0 && p.x < w {
						termbox.SetCell(p.x, y, '#', termbox.ColorGreen, termbox.ColorDefault)
						termbox.SetCell(p.x+pipeW-1, y, '#', termbox.ColorGreen, termbox.ColorDefault)
					}
				}
			}
			// Score
			tbprint(2, 0, termbox.ColorCyan, termbox.ColorDefault, fmt.Sprintf("Score: %d", score))
			tbprint(w/2-4, 0, termbox.ColorCyan, termbox.ColorDefault, fmt.Sprintf("Best: %d", best))
			termbox.Flush()

			time.Sleep(frame)
		}
	}
}

func tbprint(x, y int, fg, bg termbox.Attribute, msg string) {
	for _, ch := range msg {
		termbox.SetCell(x, y, ch, fg, bg)
		x++
	}
}
