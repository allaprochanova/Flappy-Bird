#!/usr/bin/env ruby
# flappy.rb
# encoding: UTF-8

require 'curses'
require 'json'
require 'fileutils'

RECORD_FILE = File.join(Dir.home, '.flappy_record.json')

def load_record
  return 0 unless File.exist?(RECORD_FILE)
  JSON.parse(File.read(RECORD_FILE))['record'] || 0
rescue
  0
end

def save_record(record)
  File.write(RECORD_FILE, JSON.pretty_generate(record: record))
end

Curses.init_screen
Curses.start_color
Curses.use_default_colors
Curses.init_pair(1, Curses::COLOR_YELLOW, -1)
Curses.init_pair(2, Curses::COLOR_GREEN, -1)
Curses.init_pair(3, Curses::COLOR_RED, -1)
Curses.init_pair(4, Curses::COLOR_CYAN, -1)

height = Curses.lines
width = Curses.cols
if height < 20 || width < 40
  puts "Terminal too small"
  exit 1
end

speed = 100
if ARGV.include?('-s') && ARGV.index('-s') + 1 < ARGV.size
  speed = ARGV[ARGV.index('-s') + 1].to_i
end

bird_y = height / 2
bird_x = width / 4
vel = 0
gravity = 0.5
jump = -8
pipe_w = 5
pipe_gap = 8
pipe_spacing = 25
pipes = []
score = 0
best = load_record
game_over = false
frame_time = speed / 1000.0

Curses.curs_set(0)
Curses.noecho
Curses.timeout = 0

loop do
  ch = Curses.getch
  if ch == 'q' || ch == 'Q'
    break
  elsif ch == ' '
    if !game_over
      vel = jump
      print "\a"
    else
      bird_y = height / 2
      vel = 0
      pipes = []
      score = 0
      game_over = false
      next
    end
  end

  if game_over
    Curses.clear
    msg = "💀 Game Over! Score: #{score}  Best: #{best}"
    Curses.setpos(height/2-2, (width - msg.length)/2)
    Curses.attron(Curses.color_pair(3)) { Curses.addstr(msg) }
    Curses.setpos(height/2, (width - 20)/2)
    Curses.attron(Curses.color_pair(4)) { Curses.addstr("Press SPACE to restart") }
    Curses.setpos(height/2+2, (width - 10)/2)
    Curses.attron(Curses.color_pair(4)) { Curses.addstr("Q - quit") }
    Curses.refresh
    next
  end

  # Physics
  vel += gravity
  bird_y += vel.to_i

  if bird_y <= 0 || bird_y >= height - 1
    game_over = true
    if score > best
      best = score
      save_record(best)
    end
    next
  end

  # Pipes
  if pipes.empty? || pipes.last[0] < width - pipe_spacing
    top = rand(4..height - pipe_gap - 4)
    pipes << [width, top]
  end

  # Move pipes
  pipes.each { |p| p[0] -= 2 }

  # Collisions and scoring
  new_pipes = []
  pipes.each do |x, top|
    if x < bird_x + 1 && x + pipe_w > bird_x - 1 &&
       (bird_y < top || bird_y > top + pipe_gap)
      game_over = true
      if score > best
        best = score
        save_record(best)
      end
      break
    end
    if x + pipe_w < bird_x
      score += 1
    else
      new_pipes << [x, top]
    end
  end
  pipes = new_pipes
  next if game_over

  # Draw
  Curses.clear
  # Bird
  Curses.attron(Curses.color_pair(1) | Curses::A_BOLD) do
    Curses.setpos(bird_y, bird_x)
    Curses.addstr('@')
  end
  # Pipes
  Curses.attron(Curses.color_pair(2)) do
    pipes.each do |x, top|
      (0...top).each do |y|
        if x > 0 && x < width
          Curses.setpos(y, x); Curses.addstr('#')
          Curses.setpos(y, x+pipe_w-1); Curses.addstr('#')
        end
      end
      (0...pipe_w).each do |i|
        if x+i > 0 && x+i < width
          Curses.setpos(top, x+i); Curses.addstr('#')
          Curses.setpos(top+pipe_gap, x+i); Curses.addstr('#')
        end
      end
      (top+pipe_gap...height).each do |y|
        if x > 0 && x < width
          Curses.setpos(y, x); Curses.addstr('#')
          Curses.setpos(y, x+pipe_w-1); Curses.addstr('#')
        end
      end
    end
  end
  # Score
  Curses.attron(Curses.color_pair(4)) do
    Curses.setpos(0, 2)
    Curses.addstr("Score: #{score}")
    Curses.setpos(0, width/2-4)
    Curses.addstr("Best: #{best}")
  end
  Curses.refresh
  sleep(frame_time)
end

Curses.close_screen
