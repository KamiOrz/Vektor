# Vektor Share CLI

`vektor-share` turns a local video directory into an M3U playlist, serves it over HTTP, and prints a terminal QR code for the Vektor mobile app.

## Install

```bash
cd tools/vektor-share
npm install
```

## Usage

```bash
npm start -- /path/to/videos
```

Or after linking locally:

```bash
npm link
vektor-share /path/to/videos
```

The terminal prints:

- video directory
- video count
- local HTTP service URL
- M3U URL
- QR code for the Vektor app

## Options

```bash
vektor-share <directory> \
  --port 8787 \
  --host 192.168.1.10 \
  --recursive \
  --name "Vektor Local Playlist" \
  --extensions mp4,mkv,mov \
  --covers match
```

Defaults:

- port: `8787`
- host: first non-internal IPv4 address
- scan mode: current directory only
- video extensions: `mp4,m4v,mov,mkv,webm,avi,ts,m3u8`
- cover mode: `match`

Cover modes:

- `--covers none`: do not write `tvg-logo`.
- `--covers match`: use same-name image files next to videos, such as `Movie.mp4` + `Movie.jpg`.
- `--covers ffmpeg`: use same-name images first, then generate cached JPG covers with local `ffmpeg`; generated covers default to the 10-second frame and fall back for short videos.

## Notes

- The computer and phone must be on the same local network.
- No tunneling or public network exposure is included.
- `/playlist.m3u` is generated dynamically, so newly added files appear after the next playlist request.
- `/media/...` supports HTTP `Range` requests for AVPlayer and ExoPlayer.
- `/cover/...` serves matched image files or cached ffmpeg-generated JPG covers.
