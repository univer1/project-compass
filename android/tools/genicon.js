/* 生成启动图标 PNG（API 24-25 兜底，26+ 走 adaptive icon vector）
 * 用法：node genicon.js <输出路径> [尺寸]
 * 纯 node 实现：RGBA 逐像素绘制 + zlib 压缩，无任何依赖 */
const zlib = require('zlib');
const fs = require('fs');

const SIZE = parseInt(process.argv[3] || '192', 10);
const OUT = process.argv[2];
const BG = [0x4B, 0x3F, 0xE3];          // 品牌紫
const C = SIZE / 2;

function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c;
    }
  }
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const t = Buffer.from(type, 'ascii');
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, data])));
  return Buffer.concat([len, t, data, crc]);
}

/* 几何：抗锯齿用 2x2 超采样 */
function inRoundRect(x, y, s, r) {
  const dx = Math.max(Math.abs(x - s / 2) - (s / 2 - r), 0);
  const dy = Math.max(Math.abs(y - s / 2) - (s / 2 - r), 0);
  return dx * dx + dy * dy <= r * r;
}
function inRing(x, y, r0, r1) {
  const d = Math.hypot(x - C, y - C);
  return d >= r0 && d <= r1;
}
function inCircle(x, y, r) { return Math.hypot(x - C, y - C) <= r; }
function inTri(px, py, tri) {
  const [a, b, c] = tri;
  const s1 = (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
  const s2 = (c[0] - b[0]) * (py - b[1]) - (c[1] - b[1]) * (px - b[0]);
  const s3 = (a[0] - c[0]) * (py - c[1]) - (a[1] - c[1]) * (px - c[0]);
  return (s1 >= 0 && s2 >= 0 && s3 >= 0) || (s1 <= 0 && s2 <= 0 && s3 <= 0);
}

const R_OUT = SIZE * 0.330, R_IN = SIZE * 0.292, R_HUB = SIZE * 0.047;
const L = SIZE * 0.140, W = SIZE * 0.034;
/* 东北主指针 / 西南尾指针（与 vector 图标一致） */
const triNE = [[C, C], [C + L, C - L], [C + W, C + W]];
const triSW = [[C, C], [C - L, C + L], [C - W, C - W]];

const raw = Buffer.alloc(SIZE * (SIZE * 4 + 1));
for (let y = 0; y < SIZE; y++) {
  const row = y * (SIZE * 4 + 1);
  raw[row] = 0; // filter none
  for (let x = 0; x < SIZE; x++) {
    let hit = 0;
    for (const [ox, oy] of [[0.25, 0.25], [0.75, 0.25], [0.25, 0.75], [0.75, 0.75]]) {
      const sx = x + ox, sy = y + oy;
      const white = inRing(sx, sy, R_IN, R_OUT) || inCircle(sx, sy, R_HUB)
        || inTri(sx, sy, triNE) || inTri(sx, sy, triSW);
      const inShape = inRoundRect(sx, sy, SIZE, SIZE * 0.225);
      if (white && inShape) hit += 2;         // 白色（不透明）
      else if (inShape) hit += 1;             // 背景紫（不透明）
    }
    const o = row + 1 + x * 4;
    if (hit >= 6) { raw[o] = 255; raw[o + 1] = 255; raw[o + 2] = 255; raw[o + 3] = 255; }
    else if (hit >= 3) { raw[o] = BG[0]; raw[o + 1] = BG[1]; raw[o + 2] = BG[2]; raw[o + 3] = 255; }
    else { raw[o] = 0; raw[o + 1] = 0; raw[o + 2] = 0; raw[o + 3] = 0; }
  }
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(SIZE, 0); ihdr.writeUInt32BE(SIZE, 4);
ihdr[8] = 8; ihdr[9] = 6; // 8bit RGBA
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
  chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0))
]);
fs.writeFileSync(OUT, png);
console.log('icon written:', OUT, SIZE + 'x' + SIZE, png.length + ' bytes');
