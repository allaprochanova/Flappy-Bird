// flappy.js
#!/usr/bin/env node
'use strict';

const blessed = require('blessed');
const fs = require('fs');
const path = require('path');
const os = require('os');

const RECORD_FILE = path.join(os.homedir(), '.flappy_record.json');

function loadRecord() {
    try { return JSON.parse(fs.readFileSync(RECORD_FILE)).record || 0; } catch { return 0; }
}
function saveRecord(record) {
    fs.writeFileSync(RECORD_FILE, JSON.stringify({ record }));
}

// Парсинг аргументов
let speed = 100;
if (process.argv.includes('-s') && process.argv.length > process.argv.indexOf('-s')+1) {
    speed = parseInt(process.argv[process.argv.indexOf('-s')+1]) || 100;
}

// Создаём экран
const screen = blessed.screen({
    smartCSR: true,
    title: 'Flappy Bird',
    fullUnicode: true,
});

const height = screen.height;
const width = screen.width;

if (height < 20 || width < 40) {
    console.log('Terminal too small (min 20x40)');
    process.exit(1);
}

// Игровые переменные
let birdY = Math.floor(height/2);
const birdX = Math.floor(width/4);
let vel = 0;
const gravity = 0.5;
const jump = -8;
const pipeW = 5;
const pipeGap = 8;
const pipeSpacing = 25;
let pipes = [];
let score = 0;
let best = loadRecord();
let gameOver = false;
let frameTime = speed;

// Функция рисования
function draw() {
    screen.clear();
    // Птица
    screen.fillRegion('@', birdX, birdY, birdX+1, birdY+1, blessed.colors.yellow, blessed.colors.black);
    // Трубы
    for (const p of pipes) {
        const x = p.x;
        const top = p.top;
        // Верхняя часть
        for (let y=0; y<top; y++) {
            if (x>0 && x<width) {
                screen.fillRegion('#', x, y, x+1, y+1, blessed.colors.green, blessed.colors.black);
                screen.fillRegion('#', x+pipeW-1, y, x+pipeW, y+1, blessed.colors.green, blessed.colors.black);
            }
        }
        // Горизонтальные части
        for (let i=0; i<pipeW; i++) {
            if (x+i>0 && x+i<width) {
                screen.fillRegion('#', x+i, top, x+i+1, top+1, blessed.colors.green, blessed.colors.black);
                screen.fillRegion('#', x+i, top+pipeGap, x+i+1, top+pipeGap+1, blessed.colors.green, blessed.colors.black);
            }
        }
        // Нижняя часть
        for (let y=top+pipeGap; y<height; y++) {
            if (x>0 && x<width) {
                screen.fillRegion('#', x, y, x+1, y+1, blessed.colors.green, blessed.colors.black);
                screen.fillRegion('#', x+pipeW-1, y, x+pipeW, y+1, blessed.colors.green, blessed.colors.black);
            }
        }
    }
    // Счёт
    screen.setContent(0, 2, `Score: ${score}`, blessed.colors.cyan);
    screen.setContent(0, Math.floor(width/2)-4, `Best: ${best}`, blessed.colors.cyan);
    if (gameOver) {
        screen.setContent(Math.floor(height/2)-2, Math.floor(width/2)-15, `💀 Game Over! Score: ${score}  Best: ${best}`, blessed.colors.red);
        screen.setContent(Math.floor(height/2), Math.floor(width/2)-10, 'Press SPACE to restart', blessed.colors.cyan);
        screen.setContent(Math.floor(height/2)+2, Math.floor(width/2)-5, 'Q - quit', blessed.colors.cyan);
    }
    screen.render();
}

// Игровой цикл
function update() {
    if (gameOver) {
        draw();
        return;
    }

    // Физика
    vel += gravity;
    birdY += Math.floor(vel);

    // Границы
    if (birdY <= 0 || birdY >= height-1) {
        gameOver = true;
        if (score > best) { best = score; saveRecord(best); }
        draw();
        return;
    }

    // Генерация труб
    if (pipes.length === 0 || pipes[pipes.length-1].x < width - pipeSpacing) {
        const top = Math.floor(Math.random() * (height - pipeGap - 4)) + 2;
        pipes.push({x: width, top: top});
    }

    // Движение труб
    for (const p of pipes) p.x -= 2;

    // Столкновения и счёт
    const newPipes = [];
    for (const p of pipes) {
        if (p.x < birdX+1 && p.x+pipeW > birdX-1 &&
            (birdY < p.top || birdY > p.top+pipeGap)) {
            gameOver = true;
            if (score > best) { best = score; saveRecord(best); }
            draw();
            return;
        }
        if (p.x + pipeW < birdX) {
            score++;
        } else {
            newPipes.push(p);
        }
    }
    pipes = newPipes;
    draw();
    setTimeout(update, frameTime);
}

// Управление
screen.key(['space', 'up'], function() {
    if (!gameOver) {
        vel = jump;
        // beep
        process.stdout.write('\x07');
    } else {
        // рестарт
        birdY = Math.floor(height/2);
        vel = 0;
        pipes = [];
        score = 0;
        gameOver = false;
        draw();
    }
});
screen.key(['q', 'Q'], function() { process.exit(0); });

// Старт
draw();
update();

screen.on('resize', function() {
    // Обработка изменения размера
});
