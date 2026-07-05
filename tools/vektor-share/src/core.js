import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { lookup as lookupMime } from 'mime-types';

export const DEFAULT_EXTENSIONS = ['mp4', 'm4v', 'mov', 'mkv', 'webm', 'avi', 'ts', 'm3u8'];
export const COVER_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp'];
export const GENERATED_COVER_PREFIX = '__generated';
const FFMPEG_COVER_STRATEGY_VERSION = 'seek-10s-v1';

export function parseExtensions(value) {
  if (!value) return new Set(DEFAULT_EXTENSIONS.map((ext) => `.${ext}`));
  const extensions = value
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
    .map((item) => (item.startsWith('.') ? item : `.${item}`));
  return new Set(extensions);
}

export function resolveRoot(directory) {
  if (!directory) {
    throw new Error('A video directory is required.');
  }
  const root = path.resolve(directory);
  const stat = fs.statSync(root, { throwIfNoEntry: false });
  if (!stat || !stat.isDirectory()) {
    throw new Error(`Directory not found: ${root}`);
  }
  return root;
}

export function getLocalIPv4() {
  const interfaces = os.networkInterfaces();
  for (const addresses of Object.values(interfaces)) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) {
        return address.address;
      }
    }
  }
  return '127.0.0.1';
}

export function collectVideos(root, options = {}) {
  const recursive = Boolean(options.recursive);
  const extensions = options.extensions ?? parseExtensions();
  const videos = [];

  function walk(currentDir) {
    const entries = fs.readdirSync(currentDir, { withFileTypes: true })
      .filter((entry) => !entry.name.startsWith('.'))
      .sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));

    for (const entry of entries) {
      const absolutePath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        if (recursive) walk(absolutePath);
        continue;
      }
      if (!entry.isFile()) continue;

      const ext = path.extname(entry.name).toLowerCase();
      if (!extensions.has(ext)) continue;
      const relativePath = path.relative(root, absolutePath);
      videos.push({
        absolutePath,
        relativePath,
        title: path.basename(entry.name, ext)
      });
    }
  }

  walk(root);
  return videos;
}

export function encodeMediaPath(relativePath) {
  return relativePath
    .split(path.sep)
    .map((part) => encodeURIComponent(part))
    .join('/');
}

export function mediaUrl(baseUrl, relativePath) {
  return `${baseUrl.replace(/\/$/, '')}/media/${encodeMediaPath(relativePath)}`;
}

export function coverUrl(baseUrl, relativePath) {
  return `${baseUrl.replace(/\/$/, '')}/cover/${encodeMediaPath(relativePath)}`;
}

export function findMatchingCover(root, video) {
  const parsed = path.parse(video.relativePath);
  for (const ext of COVER_EXTENSIONS) {
    const candidateRelativePath = path.join(parsed.dir, `${parsed.name}${ext}`);
    const candidateAbsolutePath = path.resolve(root, candidateRelativePath);
    if (!isInside(root, candidateAbsolutePath)) continue;
    const stat = fs.statSync(candidateAbsolutePath, { throwIfNoEntry: false });
    if (stat?.isFile()) {
      return {
        absolutePath: candidateAbsolutePath,
        relativePath: candidateRelativePath
      };
    }
  }
  return null;
}

export function ensureFfmpegAvailable(ffmpegPath = 'ffmpeg') {
  const result = spawnSync(ffmpegPath, ['-version'], { encoding: 'utf8' });
  if (result.error) {
    throw new Error(`ffmpeg not found: ${ffmpegPath}. Install ffmpeg or use --covers match.`);
  }
  if (result.status !== 0) {
    throw new Error(`ffmpeg is not available: ${result.stderr || result.stdout || ffmpegPath}`);
  }
}

export function defaultCoverCacheDir() {
  return path.join(os.tmpdir(), 'vektor-share-covers');
}

export function resolveCover(root, video, options = {}) {
  const covers = options.covers ?? 'match';
  if (covers === 'none') return null;

  const matched = findMatchingCover(root, video);
  if (matched) return matched;

  if (covers !== 'ffmpeg') return null;
  return generateFfmpegCover(video, options);
}

export function generateFfmpegCover(video, options = {}) {
  const ffmpegPath = options.ffmpegPath ?? 'ffmpeg';
  const cacheDir = options.cacheDir ?? defaultCoverCacheDir();
  const stat = fs.statSync(video.absolutePath);
  const key = crypto
    .createHash('sha1')
    .update(`${FFMPEG_COVER_STRATEGY_VERSION}\n${video.relativePath}\n${stat.mtimeMs}\n${stat.size}`)
    .digest('hex');
  const fileName = `${key}.jpg`;
  const absolutePath = path.join(cacheDir, fileName);

  fs.mkdirSync(cacheDir, { recursive: true });
  if (fs.statSync(absolutePath, { throwIfNoEntry: false })?.isFile()) {
    return {
      absolutePath,
      relativePath: path.posix.join(GENERATED_COVER_PREFIX, fileName),
      generated: true
    };
  }

  const outputArgs = ['-frames:v', '1', '-vf', 'format=yuvj420p', '-q:v', '3', '-f', 'image2', absolutePath];
  const attempts = [
    ['-y', '-hide_banner', '-loglevel', 'error', '-ss', '10', '-i', video.absolutePath, ...outputArgs],
    ['-y', '-hide_banner', '-loglevel', 'error', '-ss', '3', '-i', video.absolutePath, ...outputArgs],
    ['-y', '-hide_banner', '-loglevel', 'error', '-ss', '0.1', '-i', video.absolutePath, ...outputArgs],
    ['-y', '-hide_banner', '-loglevel', 'error', '-i', video.absolutePath, ...outputArgs]
  ];

  for (const args of attempts) {
    const result = spawnSync(ffmpegPath, args, { encoding: 'utf8' });
    if (result.status === 0 && fs.statSync(absolutePath, { throwIfNoEntry: false })?.isFile()) {
      return {
        absolutePath,
        relativePath: path.posix.join(GENERATED_COVER_PREFIX, fileName),
        generated: true
      };
    }
  }

  fs.rmSync(absolutePath, { force: true });
  return null;
}

export function buildPlaylist(videos, options) {
  const baseUrl = options.baseUrl;
  const group = options.group ?? 'Local';
  const name = options.name ?? 'Vektor Local Playlist';
  const root = options.root;
  const covers = options.covers ?? 'match';
  const lines = [
    '#EXTM3U',
    `#PLAYLIST:${escapeM3UValue(name)}`
  ];

  for (const video of videos) {
    const cover = root ? resolveCover(root, video, options) : null;
    const logo = cover ? coverUrl(baseUrl, cover.relativePath) : '';
    lines.push(`#EXTINF:-1 group-title="${escapeM3UAttribute(group)}" tvg-logo="${escapeM3UAttribute(logo)}",${escapeM3UValue(video.title)}`);
    lines.push(mediaUrl(baseUrl, video.relativePath));
  }

  return `${lines.join('\n')}\n`;
}

export function createVektorServer(config) {
  const root = config.root;
  const recursive = Boolean(config.recursive);
  const extensions = config.extensions ?? parseExtensions();
  const playlistName = config.name ?? 'Vektor Local Playlist';
  const covers = config.covers ?? 'match';
  const cacheDir = config.cacheDir ?? defaultCoverCacheDir();
  const ffmpegPath = config.ffmpegPath ?? 'ffmpeg';
  const host = config.publicHost;
  const port = config.port;
  const baseUrl = `http://${host}:${port}`;

  return http.createServer((request, response) => {
    const requestUrl = new URL(request.url ?? '/', baseUrl);

    if (request.method !== 'GET' && request.method !== 'HEAD') {
      sendText(response, 405, 'Method not allowed');
      return;
    }

    if (requestUrl.pathname === '/' || requestUrl.pathname === '/playlist.m3u') {
      const videos = collectVideos(root, { recursive, extensions });
      const playlist = buildPlaylist(videos, { baseUrl, name: playlistName, root, covers, cacheDir, ffmpegPath });
      sendBuffer(response, 200, Buffer.from(playlist, 'utf8'), {
        'Content-Type': 'audio/x-mpegurl; charset=utf-8',
        'Cache-Control': 'no-store'
      }, request.method === 'HEAD');
      return;
    }

    if (requestUrl.pathname.startsWith('/cover/')) {
      serveCover(root, cacheDir, requestUrl.pathname.slice('/cover/'.length), request, response);
      return;
    }

    if (requestUrl.pathname.startsWith('/media/')) {
      serveMedia(root, requestUrl.pathname.slice('/media/'.length), request, response);
      return;
    }

    sendText(response, 404, 'Not found');
  });
}

export function parseCoverMode(value) {
  const mode = value ?? 'match';
  if (!['none', 'match', 'ffmpeg'].includes(mode)) {
    throw new Error(`Invalid cover mode: ${value}. Use none, match, or ffmpeg.`);
  }
  return mode;
}

export function assertHasVideos(videos, root) {
  if (videos.length === 0) {
    throw new Error(`No supported video files found in ${root}`);
  }
}

function serveMedia(root, encodedRelativePath, request, response) {
  serveFile(root, encodedRelativePath, request, response, {
    invalidPathMessage: 'Invalid media path',
    allowedMime: null
  });
}

function serveCover(root, cacheDir, encodedRelativePath, request, response) {
  const generatedPrefix = `${GENERATED_COVER_PREFIX}/`;
  if (encodedRelativePath.startsWith(generatedPrefix)) {
    serveFile(cacheDir, encodedRelativePath.slice(generatedPrefix.length), request, response, {
      invalidPathMessage: 'Invalid cover path',
      allowedMime: (contentType) => ['image/jpeg'].includes(contentType)
    });
    return;
  }

  serveFile(root, encodedRelativePath, request, response, {
    invalidPathMessage: 'Invalid cover path',
    allowedMime: (contentType) => ['image/jpeg', 'image/png', 'image/webp'].includes(contentType)
  });
}

function serveFile(root, encodedRelativePath, request, response, options) {
  let relativePath;
  try {
    relativePath = encodedRelativePath
      .split('/')
      .map((part) => decodeURIComponent(part))
      .join(path.sep);
  } catch {
    sendText(response, 400, options.invalidPathMessage);
    return;
  }

  const absolutePath = path.resolve(root, relativePath);
  if (!isInside(root, absolutePath)) {
    sendText(response, 403, 'Forbidden');
    return;
  }

  const stat = fs.statSync(absolutePath, { throwIfNoEntry: false });
  if (!stat || !stat.isFile()) {
    sendText(response, 404, 'Not found');
    return;
  }

  const contentType = lookupMime(absolutePath) || 'application/octet-stream';
  if (options.allowedMime && !options.allowedMime(contentType)) {
    sendText(response, 404, 'Not found');
    return;
  }
  const range = request.headers.range;
  if (range) {
    serveRange(absolutePath, stat.size, contentType, range, request.method === 'HEAD', response);
    return;
  }

  response.writeHead(200, {
    'Content-Type': contentType,
    'Content-Length': stat.size,
    'Accept-Ranges': 'bytes'
  });
  if (request.method === 'HEAD') {
    response.end();
  } else {
    fs.createReadStream(absolutePath).pipe(response);
  }
}

function serveRange(absolutePath, size, contentType, rangeHeader, headOnly, response) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(rangeHeader);
  if (!match) {
    sendText(response, 416, 'Invalid range', { 'Content-Range': `bytes */${size}` });
    return;
  }

  let start = match[1] === '' ? undefined : Number(match[1]);
  let end = match[2] === '' ? undefined : Number(match[2]);

  if (start === undefined && end !== undefined) {
    start = Math.max(size - end, 0);
    end = size - 1;
  } else {
    start ??= 0;
    end ??= size - 1;
  }

  if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end < start || start >= size) {
    sendText(response, 416, 'Range not satisfiable', { 'Content-Range': `bytes */${size}` });
    return;
  }

  end = Math.min(end, size - 1);
  const length = end - start + 1;
  response.writeHead(206, {
    'Content-Type': contentType,
    'Content-Length': length,
    'Content-Range': `bytes ${start}-${end}/${size}`,
    'Accept-Ranges': 'bytes'
  });
  if (headOnly) {
    response.end();
  } else {
    fs.createReadStream(absolutePath, { start, end }).pipe(response);
  }
}

function sendText(response, status, text, headers = {}) {
  sendBuffer(response, status, Buffer.from(`${text}\n`, 'utf8'), {
    'Content-Type': 'text/plain; charset=utf-8',
    ...headers
  });
}

function sendBuffer(response, status, body, headers = {}, headOnly = false) {
  response.writeHead(status, {
    'Content-Length': body.length,
    ...headers
  });
  response.end(headOnly ? undefined : body);
}

function isInside(root, target) {
  const relative = path.relative(root, target);
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

function escapeM3UAttribute(value) {
  return String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;');
}

function escapeM3UValue(value) {
  return String(value).replace(/\r?\n/g, ' ').trim();
}
