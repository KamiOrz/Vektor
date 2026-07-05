#!/usr/bin/env node

import { Command } from 'commander';
import qrcode from 'qrcode-terminal';
import {
  assertHasVideos,
  collectVideos,
  createVektorServer,
  ensureFfmpegAvailable,
  getLocalIPv4,
  parseCoverMode,
  parseExtensions,
  resolveRoot
} from './core.js';

const program = new Command();

program
  .name('vektor-share')
  .description('Serve a local video directory as an M3U playlist for Vektor.')
  .argument('<directory>', 'directory containing local video files')
  .option('-p, --port <number>', 'HTTP port', parsePort, 8787)
  .option('--host <ip-or-hostname>', 'public host/IP to put in the M3U URL')
  .option('-r, --recursive', 'scan directories recursively', false)
  .option('-n, --name <playlist-name>', 'playlist name', 'Vektor Local Playlist')
  .option('-e, --extensions <csv>', 'comma-separated video extensions, e.g. mp4,mkv,mov')
  .option('--covers <mode>', 'cover mode: none, match, or ffmpeg', parseCoverMode, 'match')
  .action((directory, options) => {
    try {
      start(directory, options);
    } catch (error) {
      console.error(`Error: ${error.message}`);
      process.exitCode = 1;
    }
  });

program.parse();

function start(directory, options) {
  const root = resolveRoot(directory);
  const extensions = parseExtensions(options.extensions);
  const covers = options.covers;
  if (covers === 'ffmpeg') {
    ensureFfmpegAvailable();
  }
  const publicHost = options.host || getLocalIPv4();
  const videos = collectVideos(root, {
    recursive: options.recursive,
    extensions
  });
  assertHasVideos(videos, root);

  const server = createVektorServer({
    root,
    recursive: options.recursive,
    extensions,
    name: options.name,
    covers,
    publicHost,
    port: options.port
  });

  server.listen(options.port, '0.0.0.0', () => {
    const address = `http://${publicHost}:${options.port}`;
    const playlistUrl = `${address}/playlist.m3u`;

    console.log('');
    console.log('VEKTOR LOCAL SHARE');
    console.log('------------------');
    console.log(`Video directory : ${root}`);
    console.log(`Video count     : ${videos.length}`);
    console.log(`Cover mode      : ${covers}`);
    console.log(`Service address : ${address}`);
    console.log(`M3U URL         : ${playlistUrl}`);
    console.log('');
    qrcode.generate(playlistUrl, { small: true });
    console.log('');
    console.log('Scan the QR code with Vektor. Press Ctrl+C to stop.');
    console.log('');
  });

  server.on('error', (error) => {
    console.error(`Server error: ${error.message}`);
    process.exitCode = 1;
    server.close();
  });

  process.on('SIGINT', () => {
    console.log('\nStopping Vektor local share...');
    server.close(() => process.exit(0));
  });
}

function parsePort(value) {
  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`Invalid port: ${value}`);
  }
  return port;
}
