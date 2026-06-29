# flappy.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import time
import random
import json
import argparse
import curses
from pathlib import Path

# Конфигурация
RECORD_FILE = Path.home() / '.flappy_record.json'

def load_record():
    try:
        with open(RECORD_FILE, 'r') as f:
            return json.load(f).get('record', 0)
    except:
        return 0

def save_record(record):
    with open(RECORD_FILE, 'w') as f:
        json.dump({'record': record}, f)

def main(stdscr, speed):
    # Настройка curses
    curses.curs_set(0)
    stdscr.nodelay(1)
    stdscr.timeout(0)
    curses.start_color()
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_YELLOW, -1)   # птица
    curses.init_pair(2, curses.COLOR_GREEN, -1)   # трубы
    curses.init_pair(3, curses.COLOR_RED, -1)     # смерть
    curses.init_pair(4, curses.COLOR_CYAN, -1)    # счёт

    height, width = stdscr.getmaxyx()
    if height < 20 or width < 40:
        print("Терминал слишком мал. Нужно минимум 20x40.")
        return

    # Игровые параметры
    bird_y = height // 2
    bird_x = width // 4
    velocity = 0
    gravity = 0.5
    jump_strength = -8
    pipe_width = 5
    pipe_gap = 8
    pipe_spacing = 25
    pipes = []
    score = 0
    best = load_record()
    game_over = False
    frame_time = speed / 1000.0

    # Основной цикл
    while True:
        # Ввод
        key = stdscr.getch()
        if key == ord('q') or key == ord('Q'):
            break
        if key == ord(' ') or key == ord('w') or key == ord('W'):
            if not game_over:
                velocity = jump_strength
                # звук (ASCII bell)
                stdscr.addstr(0, 0, '\a')
            else:
                # перезапуск
                bird_y = height // 2
                velocity = 0
                pipes = []
                score = 0
                game_over = False
                continue

        if game_over:
            # Показываем сообщение
            stdscr.clear()
            msg = f"💀 Игра окончена! Счёт: {score}  Рекорд: {best}"
            stdscr.addstr(height//2 - 2, (width - len(msg))//2, msg, curses.color_pair(3))
            stdscr.addstr(height//2, (width - 20)//2, "Нажмите пробел для рестарта", curses.color_pair(4))
            stdscr.addstr(height//2 + 2, (width - 20)//2, "Q - выход", curses.color_pair(4))
            stdscr.refresh()
            continue

        # Обновление физики
        velocity += gravity
        bird_y += velocity

        # Проверка границ
        if bird_y <= 0 or bird_y >= height - 1:
            game_over = True
            if score > best:
                best = score
                save_record(best)
            continue

        # Генерация труб
        if not pipes or pipes[-1][0] < width - pipe_spacing:
            pipe_height = random.randint(4, height - pipe_gap - 4)
            pipes.append([width, pipe_height])

        # Движение труб
        for pipe in pipes:
            pipe[0] -= 2  # скорость движения

        # Проверка столкновений и счёт
        remove = []
        for pipe in pipes:
            x, top_height = pipe
            bottom_y = top_height + pipe_gap
            # Проверка попадания в трубу
            if (x < bird_x + 1 and x + pipe_width > bird_x - 1 and
                (bird_y < top_height or bird_y > bottom_y)):
                game_over = True
                if score > best:
                    best = score
                    save_record(best)
                break
            # Если птица прошла трубу
            if x + pipe_width < bird_x:
                score += 1
                remove.append(pipe)
        pipes = [p for p in pipes if p not in remove]

        # Отрисовка
        stdscr.clear()
        # Фон (небо)
        for i in range(height):
            stdscr.addch(i, 0, ' ', curses.color_pair(1))
        # Птица
        stdscr.addch(bird_y, bird_x, '@', curses.color_pair(1) | curses.A_BOLD)
        # Трубы
        for pipe in pipes:
            x, top = pipe
            for y in range(top):
                if 0 < x < width:
                    stdscr.addch(y, x, '#', curses.color_pair(2))
                    stdscr.addch(y, x+pipe_width-1, '#', curses.color_pair(2))
                for i in range(pipe_width):
                    if 0 < x+i < width:
                        stdscr.addch(top, x+i, '#', curses.color_pair(2))
                        stdscr.addch(top+pipe_gap, x+i, '#', curses.color_pair(2))
            for y in range(top+pipe_gap, height):
                if 0 < x < width:
                    stdscr.addch(y, x, '#', curses.color_pair(2))
                    stdscr.addch(y, x+pipe_width-1, '#', curses.color_pair(2))
        # Счёт
        stdscr.addstr(0, 2, f"Счёт: {score}", curses.color_pair(4))
        stdscr.addstr(0, width//2 - 4, f"Рекорд: {best}", curses.color_pair(4))
        stdscr.refresh()

        time.sleep(frame_time)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--speed', type=int, default=100, help='Скорость (мс)')
    args = parser.parse_args()
    try:
        curses.wrapper(main, args.speed)
    except KeyboardInterrupt:
        print("\nИгра завершена.")
