// Turns the screencast frames and narration clips from run-ui-flow.mjs into one 1920x1080 H.264 + AAC MP4.
// Frames keep their real timing, and each narration clip starts at the moment its caption appeared.
import ffmpeg from '@ffmpeg-installer/ffmpeg'
import { execFileSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const OUT = join(import.meta.dirname, 'out')
const DELIVER = join(import.meta.dirname, 'deliverables')
mkdirSync(DELIVER, { recursive: true })
const frames = JSON.parse(readFileSync(join(OUT, 'frames.json'), 'utf8'))
if (frames.length < 2) throw new Error('No frames recorded')
const t0 = frames[0].t
const speechTail = (() => { try { const s = JSON.parse(readFileSync(join(OUT, 'speech.json'), 'utf8')); return s.length ? s.at(-1).t + s.at(-1).dur + 1.5 : 0 } catch { return 0 } })()
const end = Math.max(frames.at(-1).t + 3, speechTail)

// Video: each frame stays on screen until the next one arrives (exact timing keeps the voice in sync).
const lines = []
for (let i = 0; i < frames.length; i++) {
  const d = (frames[i + 1]?.t ?? end) - frames[i].t
  lines.push(`file '${frames[i].file.replace(/\\/g, '/')}'`, `duration ${Math.max(d, 0.001).toFixed(4)}`)
}
lines.push(`file '${frames.at(-1).file.replace(/\\/g, '/')}'`)
const list = join(OUT, 'frames.txt')
writeFileSync(list, lines.join('\n'))

// Audio: place every narration clip (16-bit PCM WAV from the Windows voice) on one timeline.
const speechFile = join(OUT, 'speech.json')
const speech = existsSync(speechFile) ? JSON.parse(readFileSync(speechFile, 'utf8')) : []
let narration = null
if (speech.length) {
  const wav = (f) => {
    const b = readFileSync(f)
    const fmt = b.indexOf('fmt ')
    const channels = b.readUInt16LE(fmt + 10), rate = b.readUInt32LE(fmt + 12), bits = b.readUInt16LE(fmt + 22)
    const data = b.indexOf('data')
    return { channels, rate, bits, pcm: b.subarray(data + 8, data + 8 + b.readUInt32LE(data + 4)) }
  }
  const first = wav(speech[0].file)
  if (first.bits !== 16) throw new Error('Expected 16-bit narration clips')
  const bytesPerSec = first.rate * first.channels * 2
  const total = Buffer.alloc(Math.ceil((end - t0 + 2) * bytesPerSec) & ~1)
  for (const s of speech) {
    const clip = wav(s.file)
    let at = Math.round((s.t - t0) * bytesPerSec)
    at -= at % (first.channels * 2)
    clip.pcm.copy(total, Math.max(0, at), 0, Math.min(clip.pcm.length, total.length - Math.max(0, at)))
  }
  const header = Buffer.alloc(44)
  header.write('RIFF', 0); header.writeUInt32LE(36 + total.length, 4); header.write('WAVE', 8)
  header.write('fmt ', 12); header.writeUInt32LE(16, 16); header.writeUInt16LE(1, 20); header.writeUInt16LE(first.channels, 22)
  header.writeUInt32LE(first.rate, 24); header.writeUInt32LE(bytesPerSec, 28); header.writeUInt16LE(first.channels * 2, 32); header.writeUInt16LE(16, 34)
  header.write('data', 36); header.writeUInt32LE(total.length, 40)
  narration = join(OUT, 'narration.wav')
  writeFileSync(narration, Buffer.concat([header, total]))
}

const target = join(DELIVER, 'PrimePay_User_Hierarchy_Commission_Flow.mp4')
execFileSync(ffmpeg.path, [
  '-y', '-f', 'concat', '-safe', '0', '-i', list,
  ...(narration ? ['-i', narration] : []),
  '-vf', 'scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=white,fps=25,setsar=1,format=yuv420p',
  '-c:v', 'libx264', '-preset', 'medium', '-crf', '23',
  ...(narration ? ['-c:a', 'aac', '-b:a', '128k', '-ar', '44100', '-map', '0:v:0', '-map', '1:a:0'] : []),
  '-movflags', '+faststart',
  '-metadata', 'title=PrimePay complete application walkthrough (development data, simulated money)',
  target,
], { stdio: ['ignore', 'ignore', 'inherit'] })
console.log(`${target}  ${(statSync(target).size / 1e6).toFixed(1)} MB  ${Math.round(end - t0)} s  ${speech.length} narration clips`)
