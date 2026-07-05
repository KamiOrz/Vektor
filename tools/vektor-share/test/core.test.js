import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { after, before, describe, it } from 'node:test';
import {
  buildPlaylist,
  collectVideos,
  createVektorServer,
  encodeMediaPath,
  findMatchingCover,
  generateFfmpegCover,
  parseExtensions,
  resolveRoot
} from '../src/core.js';

describe('vektor-share core', () => {
  let tempDir;
  let fakeFfmpegPath;
  let cacheDir;

  before(() => {
    tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'vektor-share-'));
    cacheDir = path.join(tempDir, 'cache');
    fs.writeFileSync(path.join(tempDir, 'A Video.mp4'), '0123456789');
    fs.writeFileSync(path.join(tempDir, 'A Video.jpg'), 'fake jpg');
    fs.writeFileSync(path.join(tempDir, 'No Cover.mp4'), 'no-cover-video');
    fs.writeFileSync(path.join(tempDir, 'notes.txt'), 'ignore me');
    fs.writeFileSync(path.join(tempDir, '中文 片段.mkv'), 'abcdef');
    fs.mkdirSync(path.join(tempDir, 'Nested'));
    fs.writeFileSync(path.join(tempDir, 'Nested', 'Clip.mov'), 'nested');
    fakeFfmpegPath = path.join(tempDir, 'fake-ffmpeg.sh');
    fs.writeFileSync(fakeFfmpegPath, '#!/bin/sh\nif [ "$1" = "-version" ]; then echo "ffmpeg fake"; exit 0; fi\nout=""\nfor arg do out="$arg"; done\nprintf "jpg" > "$out"\n');
    fs.chmodSync(fakeFfmpegPath, 0o755);
  });

  after(() => {
    fs.rmSync(tempDir, { recursive: true, force: true });
  });

  it('requires an existing directory', () => {
    assert.equal(resolveRoot(tempDir), tempDir);
    assert.throws(() => resolveRoot(path.join(tempDir, 'missing')), /Directory not found/);
  });

  it('collects only supported videos by default', () => {
    const videos = collectVideos(tempDir);
    assert.deepEqual(videos.map((video) => video.relativePath), ['A Video.mp4', 'No Cover.mp4', '中文 片段.mkv']);
  });

  it('collects nested videos when recursive is enabled', () => {
    const videos = collectVideos(tempDir, { recursive: true });
    assert.deepEqual(videos.map((video) => video.relativePath), [
      'A Video.mp4',
      path.join('Nested', 'Clip.mov'),
      'No Cover.mp4',
      '中文 片段.mkv'
    ]);
  });

  it('honors extension overrides', () => {
    const videos = collectVideos(tempDir, { extensions: parseExtensions('txt') });
    assert.deepEqual(videos.map((video) => video.relativePath), ['notes.txt']);
  });

  it('encodes media URL path segments', () => {
    assert.equal(
      encodeMediaPath(path.join('Nested Dir', '中文 片段.mp4')),
      'Nested%20Dir/%E4%B8%AD%E6%96%87%20%E7%89%87%E6%AE%B5.mp4'
    );
  });

  it('builds an M3U playlist with encoded media URLs', () => {
    const videos = collectVideos(tempDir);
    const playlist = buildPlaylist(videos, {
      baseUrl: 'http://192.168.1.10:8787',
      name: 'Local Test',
      root: tempDir
    });
    assert.match(playlist, /^#EXTM3U\n#PLAYLIST:Local Test\n/);
    assert.match(playlist, /#EXTINF:-1 group-title="Local" tvg-logo="http:\/\/192\.168\.1\.10:8787\/cover\/A%20Video\.jpg",A Video/);
    assert.match(playlist, /http:\/\/192\.168\.1\.10:8787\/media\/A%20Video\.mp4/);
    assert.match(playlist, /%E4%B8%AD%E6%96%87%20%E7%89%87%E6%AE%B5\.mkv/);
  });

  it('can disable cover URLs', () => {
    const videos = collectVideos(tempDir);
    const playlist = buildPlaylist(videos, {
      baseUrl: 'http://192.168.1.10:8787',
      root: tempDir,
      covers: 'none'
    });
    assert.match(playlist, /#EXTINF:-1 group-title="Local" tvg-logo="",A Video/);
  });

  it('finds same-name cover images', () => {
    const [video] = collectVideos(tempDir);
    assert.equal(findMatchingCover(tempDir, video).relativePath, 'A Video.jpg');
  });

  it('generates cached cover images with ffmpeg mode', () => {
    const video = collectVideos(tempDir).find((item) => item.relativePath === 'No Cover.mp4');
    const cover = generateFfmpegCover(video, { ffmpegPath: fakeFfmpegPath, cacheDir });
    assert.equal(cover.generated, true);
    assert.match(cover.relativePath, /^__generated\/[a-f0-9]+\.jpg$/);
    assert.equal(fs.readFileSync(cover.absolutePath, 'utf8'), 'jpg');

    const playlist = buildPlaylist([video], {
      baseUrl: 'http://192.168.1.10:8787',
      root: tempDir,
      covers: 'ffmpeg',
      ffmpegPath: fakeFfmpegPath,
      cacheDir
    });
    assert.match(playlist, /tvg-logo="http:\/\/192\.168\.1\.10:8787\/cover\/__generated\/[a-f0-9]+\.jpg"/);
  });

  it('serves playlist, media range requests, and blocks traversal', async () => {
    const server = createVektorServer({
      root: tempDir,
      recursive: false,
      extensions: parseExtensions(),
      name: 'HTTP Test',
      publicHost: '127.0.0.1',
      port: 0,
      covers: 'ffmpeg',
      ffmpegPath: fakeFfmpegPath,
      cacheDir
    });
    await listen(server);
    try {
      const { port } = server.address();
      const playlist = await fetch(`http://127.0.0.1:${port}/playlist.m3u`);
      assert.equal(playlist.status, 200);
      assert.equal(playlist.headers.get('content-type'), 'audio/x-mpegurl; charset=utf-8');
      const playlistText = await playlist.text();
      assert.match(playlistText, /#PLAYLIST:HTTP Test/);

      const media = await fetch(`http://127.0.0.1:${port}/media/A%20Video.mp4`, {
        headers: { Range: 'bytes=2-5' }
      });
      assert.equal(media.status, 206);
      assert.equal(media.headers.get('content-range'), 'bytes 2-5/10');
      assert.equal(await media.text(), '2345');

      const traversal = await fetch(`http://127.0.0.1:${port}/media/..%2Fsecret.mp4`);
      assert.equal(traversal.status, 403);

      const cover = await fetch(`http://127.0.0.1:${port}/cover/A%20Video.jpg`);
      assert.equal(cover.status, 200);
      assert.equal(cover.headers.get('content-type'), 'image/jpeg');

      const coverTraversal = await fetch(`http://127.0.0.1:${port}/cover/..%2Fsecret.jpg`);
      assert.equal(coverTraversal.status, 403);

      const generatedCoverPath = playlistText.match(/\/cover\/(__generated\/[a-f0-9]+\.jpg)/)?.[1];
      if (generatedCoverPath) {
        const generatedCover = await fetch(`http://127.0.0.1:${port}/cover/${generatedCoverPath}`);
        assert.equal(generatedCover.status, 200);
        assert.equal(generatedCover.headers.get('content-type'), 'image/jpeg');
      }
    } finally {
      await close(server);
    }
  });
});

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
}

function close(server) {
  return new Promise((resolve) => server.close(resolve));
}
