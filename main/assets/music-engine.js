/* music-engine.js
 * Procedural + XML music engine for ChillSpace games.
 * Exposes: window.ChillSpaceMusic
 *
 * Optional vocal synthesis requires (same folder):
 *   klattsch.bundle.js    (exposes window.Klattsch)
 *   formant-worklet.js    (bundled AudioWorklet, loaded on first use)
 * If those aren't present, songs play instrumentally.
 */
(function (global) {
'use strict';

/* ============================================================
 * 1. CONSTANTS
 * ============================================================ */

const KEY_PCS = [0,1,2,3,4,5,6,7,8,9,10,11];

const SCALES = [
    [0,2,4,5,7,9,11],[0,2,4,6,7,9,11],[0,2,4,5,7,9,10],
    [0,2,3,5,7,8,10],[0,2,3,5,7,9,10],[0,1,3,5,7,8,10],
    [0,1,3,5,7,8,11],[0,2,3,5,7,8,11],[0,2,3,5,7,9,11],
    [0,2,3,6,7,8,11],[0,2,4,7,9],[0,3,5,7,10],
    [0,2,3,5,6,8,9,11],[0,2,4,6,8,10],[0,1,3,4,6,7,9,10],
    [0,2,3,4,7,9],[0,2,4,5,7,8,10],[0,1,4,5,7,8,11],
    [0,2,5,7,9],[0,3,4,7,8,11],[0,1,2,5,7,8,10],[0,2,3,6,7,8,10]
];

const PROGS = [
    [0,4,5,3],[0,5,3,4],[0,4,3,5],[5,3,0,4],[3,4,0,5],
    [5,3,4,0],[0,3,4,5],[0,6,5,4],[5,2,3,0],
    [0,2,5,4],[0,5,2,6],[0,6,3,5],[0,1,6,5],
    [1,4,0,3],[0,3,1,4],[1,5,4,3],[0,1,4,5],[2,5,1,4],
    [0,0,5,5],[0,0,3,4],[0,5],[0,3],[0,4],[0,0,0,4],
    [0,1,2,3],[4,3,2,1],[0,2,4,6],[6,4,2,0],
    [0,5,3,4,0,4,5,3],[0,3,4,5,0,3,4,5],[0,4,5,3,2,5,0,4],
    [0,2,4,2],[5,5,3,3],[0,6]
];

const SECTION_CONFIG = {
    intro:     { bars:[2,2,4], energy:0.25, parts:['pad'],                                    velScale:0.55, octaveShift:0 },
    verse:     { bars:[4,4,8], energy:0.55, parts:['drums','bass','pad','arp'],                velScale:0.78, octaveShift:0 },
    prechorus: { bars:[2,2,4], energy:0.78, parts:['drums','bass','pad','arp'],                velScale:0.92, octaveShift:0, build:true },
    chorus:    { bars:[4,4,8], energy:1.0,  parts:['drums','bass','pad','guitar','arp','lead'],velScale:1.0,  octaveShift:1, crash:true },
    bridge:    { bars:[2,4],   energy:0.4,  parts:['pad','arp'],                               velScale:0.65, octaveShift:0 },
    breakdown: { bars:[2,4],   energy:0.35, parts:['pad','arp'],                               velScale:0.6,  octaveShift:0 },
    build:     { bars:[2,4],   energy:0.78, parts:['drums','bass','arp'],                      velScale:0.88, octaveShift:0, build:true },
    drop:      { bars:[4,8],   energy:1.0,  parts:['drums','bass','pad','arp','lead'],         velScale:1.0,  octaveShift:1, crash:true },
    outro:     { bars:[2,4],   energy:0.15, parts:['pad'],                                     velScale:0.45, octaveShift:0 }
};

const SONG_FORMS = [
    ['intro','verse','chorus','verse','chorus','outro'],
    ['intro','verse','prechorus','chorus','verse','prechorus','chorus','outro'],
    ['intro','verse','prechorus','chorus','bridge','chorus','chorus','outro'],
    ['intro','build','drop','breakdown','build','drop','outro'],
    ['intro','verse','chorus','outro'],
    ['intro','verse','prechorus','chorus','verse','prechorus','chorus','bridge','chorus','outro'],
    ['intro','verse','chorus','bridge','chorus','outro'],
    ['intro','verse','verse','chorus','verse','chorus','outro'],
    ['intro','verse','prechorus','chorus','breakdown','chorus','outro'],
    ['intro','verse','chorus','verse','chorus','bridge','chorus','chorus','outro']
];

const PAD_WAVES  = ['sawtooth','triangle','square','sine'];
const BASS_WAVES = ['sawtooth','square','triangle'];
const SUB_WAVES  = ['sine','triangle'];
const LEAD_WAVES = ['sawtooth','square','triangle','sine'];
const ARP_WAVES  = ['triangle','sine','square'];
const GTR_WAVES  = ['sawtooth','square'];

/* ============================================================
 * 2. HANGUL → ARPABET
 * ============================================================ */

const HANGUL_CONS = {
    'ㄱ':{i:'G', f:'K'},  'ㄲ':{i:'G', f:'K'},  'ㄴ':{i:'N', f:'N'},
    'ㄷ':{i:'D', f:'T'},  'ㄸ':{i:'D', f:'T'},  'ㄹ':{i:'R', f:'L'},
    'ㅁ':{i:'M', f:'M'},  'ㅂ':{i:'B', f:'P'},  'ㅃ':{i:'B', f:'P'},
    'ㅅ':{i:'S', f:'T'},  'ㅆ':{i:'S', f:'T'},  'ㅇ':{i:'',  f:'NG'},
    'ㅈ':{i:'JH',f:'T'},  'ㅉ':{i:'JH',f:'T'},  'ㅊ':{i:'CH',f:'T'},
    'ㅋ':{i:'K', f:'K'},  'ㅌ':{i:'T', f:'T'},  'ㅍ':{i:'P', f:'P'},
    'ㅎ':{i:'HH',f:'T'}
};

const HANGUL_VOWELS = {
    'ㅏ':['AA'],'ㅐ':['AE'],'ㅑ':['Y','AA'],'ㅒ':['Y','AE'],
    'ㅓ':['AH'],'ㅔ':['EH'],'ㅕ':['Y','AH'],'ㅖ':['Y','EH'],
    'ㅗ':['OW'],'ㅘ':['W','AA'],'ㅙ':['W','AE'],'ㅚ':['W','EH'],
    'ㅛ':['Y','OW'],'ㅜ':['UW'],'ㅝ':['W','AH'],'ㅞ':['W','EH'],
    'ㅟ':['W','IY'],'ㅠ':['Y','UW'],'ㅡ':['IH'],'ㅢ':['UW','IY'],
    'ㅣ':['IY']
};

const HANGUL_INITIALS = ['ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ','ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'];
const HANGUL_MEDIALS  = ['ㅏ','ㅐ','ㅑ','ㅒ','ㅓ','ㅔ','ㅕ','ㅖ','ㅗ','ㅘ','ㅙ','ㅚ','ㅛ','ㅜ','ㅝ','ㅞ','ㅟ','ㅠ','ㅡ','ㅢ','ㅣ'];
const HANGUL_FINALS   = ['','ㄱ','ㄲ','ㄳ','ㄴ','ㄵ','ㄶ','ㄷ','ㄹ','ㄺ','ㄻ','ㄼ','ㄽ','ㄾ','ㄿ','ㅀ','ㅁ','ㅂ','ㅄ','ㅅ','ㅆ','ㅇ','ㅈ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'];

function isVowelJamo(ch)   { return ch in HANGUL_VOWELS; }
function isConsonantJamo(ch){ return ch in HANGUL_CONS;   }

function hasHangul(s) {
    for (const ch of s) {
        const c = ch.charCodeAt(0);
        if ((c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x3130 && c <= 0x318F)) return true;
    }
    return false;
}

function hangulToArpabet(text) {
    const out = [];
    const chars = [...text];
    for (let idx = 0; idx < chars.length; idx++) {
        const ch = chars[idx];
        const code = ch.charCodeAt(0);

        if (code >= 0xAC00 && code <= 0xD7A3) {
            const off = code - 0xAC00;
            const i = Math.floor(off / (21 * 28));
            const m = Math.floor((off % (21 * 28)) / 28);
            const f = off % 28;
            const ini = HANGUL_CONS[HANGUL_INITIALS[i]];
            if (ini && ini.i) out.push(ini.i);
            const med = HANGUL_VOWELS[HANGUL_MEDIALS[m]];
            if (med) out.push(...med);
            if (f > 0) {
                const fin = HANGUL_CONS[HANGUL_FINALS[f]];
                if (fin && fin.f) out.push(fin.f);
            }
            continue;
        }

        if (isVowelJamo(ch)) { out.push(...HANGUL_VOWELS[ch]); continue; }
        if (isConsonantJamo(ch)) {
            const prev = idx > 0 ? chars[idx - 1] : null;
            const next = idx + 1 < chars.length ? chars[idx + 1] : null;
            const isFinal = prev && isVowelJamo(prev) && (!next || !isVowelJamo(next));
            const info = HANGUL_CONS[ch];
            const ph = isFinal ? info.f : info.i;
            if (ph) out.push(ph);
            continue;
        }

        if (/\s/.test(ch)) continue;
        const up = ch.toUpperCase();
        if (/[A-Z]/.test(up)) out.push(up);
    }
    return out;
}

function textToArpabet(text) {
    const s = String(text || '').trim();
    if (!s) return [];
    if (hasHangul(s)) return hangulToArpabet(s);
    return s.toUpperCase().split(/\s+/).filter(Boolean);
}

/* ============================================================
 * 3. XML PARSER
 * ============================================================ */

const PITCH_RE = /^([A-Ga-g])(#{1,2}|b{1,2})?(-?\d{1,2})$/;
const SEMITONES = { C:0, D:2, E:4, F:5, G:7, A:9, B:11 };

const INSTRUMENT_KIND_MAP = {
    kick:'kick', snare:'snare', hat:'hat', crash:'crash',
    square:'synth', sawtooth:'synth', lead:'synth', synth:'synth',
    triangle:'bell', sine:'bell', bell:'bell',
    piano:'piano', pluck:'piano', epiano:'piano',
    guitar:'guitar',
    pad:'pad', strings:'pad',
    bass:'bass',
    organ:'bell', music_box:'bell'
};

function pitchToMidi(s) {
    const m = PITCH_RE.exec(String(s).trim());
    if (!m) return null;
    let semi = SEMITONES[m[1].toUpperCase()];
    const acc = m[2] || '';
    if (acc === '#')  semi += 1;
    if (acc === '##') semi += 2;
    if (acc === 'b')  semi -= 1;
    if (acc === 'bb') semi -= 2;
    return (parseInt(m[3], 10) + 1) * 12 + semi;
}

function midiToNoteName(m) {
    const N = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
    return N[((m % 12) + 12) % 12] + (Math.floor(m / 12) - 1);
}

function localName(el) { return (el.localName || el.tagName || '').toLowerCase(); }
function attr(el, name) { return (el.hasAttribute && el.hasAttribute(name)) ? el.getAttribute(name) : null; }
function numOrNull(v) {
    if (v === null || v === undefined || String(v).trim() === '') return null;
    const n = parseFloat(v);
    return Number.isFinite(n) ? n : null;
}
function numFrom(v, d) { const n = numOrNull(v); return n === null ? d : n; }
function clamp(v, lo, hi) { return Math.min(hi, Math.max(lo, v)); }

function parseSongXml(xmlText) {
    const doc = new DOMParser().parseFromString(xmlText, 'application/xml');
    const perr = doc.getElementsByTagName('parsererror')[0] ||
                 doc.getElementsByTagNameNS('*', 'parsererror')[0];
    if (perr) throw new Error('XML parse error: ' + (perr.textContent || '').slice(0, 200));

    const root = doc.documentElement;
    if (!root || localName(root) !== 'song') throw new Error('Root must be <song>');

    const tempo = clamp(numFrom(attr(root, 'tempo'), 120), 20, 400);

    const song = {
        source: 'xml',
        title: attr(root, 'title') || 'Untitled',
        tempo: tempo,
        key: attr(root, 'key') || null,
        tracks: [],
        events: [],
        duration: 0,
        totalBeats: 0,
        hasLyrics: false
    };

    const trackEls = Array.from(root.children).filter(e => localName(e) === 'track');

    for (let ti = 0; ti < trackEls.length; ti++) {
        const te = trackEls[ti];
        const instrRaw = (attr(te, 'instrument') || 'synth').trim().toLowerCase();
        const kind = INSTRUMENT_KIND_MAP[instrRaw] || 'synth';
        const volume = clamp(numFrom(attr(te, 'volume'), 0.7), 0, 1);
        const pan    = clamp(numFrom(attr(te, 'pan'), 0), -1, 1);

        const track = {
            name: attr(te, 'name') || ('Track ' + (ti + 1)),
            instrument: instrRaw, kind, volume, pan
        };
        song.tracks.push(track);

        let cursor = 0;

        for (const el of Array.from(te.children)) {
            const tag = localName(el);

            if (tag === 'note') {
                let midi = null;
                const midiAttr = numOrNull(attr(el, 'midi'));
                if (midiAttr !== null) midi = Math.round(midiAttr);
                if (midi === null && attr(el, 'pitch')) midi = pitchToMidi(attr(el, 'pitch'));
                if (midi === null) continue;

                const t = numOrNull(attr(el, 'time'));
                const time = t !== null ? Math.max(0, t) : cursor;
                const dur  = Math.max(0.001, numFrom(attr(el, 'duration'), 1));
                const vel  = clamp(numFrom(attr(el, 'velocity'), 0.8), 0, 1);

                const lyricText = (el.textContent || '').trim();
                const lyric = lyricText ? textToArpabet(lyricText) : null;
                if (lyric) song.hasLyrics = true;

                song.events.push({
                    time, duration: dur, kind,
                    notes: [midi],
                    velocity: vel,
                    track, octaveShift: 0,
                    lyric: lyric
                });
                cursor = time + dur;

            } else if (tag === 'chord') {
                const t = numOrNull(attr(el, 'time'));
                const time = t !== null ? Math.max(0, t) : cursor;
                const chordDur = numOrNull(attr(el, 'duration'));
                const notes = [];
                let maxDur = 0;
                let chordLyric = null;

                for (const ne of Array.from(el.children)) {
                    if (localName(ne) !== 'note') continue;
                    let midi = null;
                    const midiAttr = numOrNull(attr(ne, 'midi'));
                    if (midiAttr !== null) midi = Math.round(midiAttr);
                    if (midi === null && attr(ne, 'pitch')) midi = pitchToMidi(attr(ne, 'pitch'));
                    if (midi === null) continue;
                    notes.push(midi);
                    const d = chordDur !== null ? chordDur : numFrom(attr(ne, 'duration'), 1);
                    if (d > maxDur) maxDur = d;
                    if (!chordLyric) {
                        const lt = (ne.textContent || '').trim();
                        if (lt) chordLyric = textToArpabet(lt);
                    }
                }

                if (notes.length === 0) continue;
                if (chordLyric) song.hasLyrics = true;

                song.events.push({
                    time,
                    duration: Math.max(0.001, maxDur || chordDur || 1),
                    kind,
                    notes,
                    velocity: clamp(numFrom(attr(el, 'velocity'), 0.7), 0, 1),
                    track, octaveShift: 0,
                    lyric: chordLyric
                });
                cursor = time + (maxDur || chordDur || 1);

            } else if (tag === 'rest') {
                const t = numOrNull(attr(el, 'time'));
                const time = t !== null ? Math.max(0, t) : cursor;
                cursor = time + Math.max(0, numFrom(attr(el, 'duration'), 1));
            }
        }
    }

    song.events.sort((a, b) => {
        if (a.time !== b.time) return a.time - b.time;
        return (b.lyric ? 1 : 0) - (a.lyric ? 1 : 0);
    });

    let maxEnd = 0;
    for (const ev of song.events) maxEnd = Math.max(maxEnd, ev.time + ev.duration);
    song.totalBeats = maxEnd + 2;
    song.duration = maxEnd;

    return song;
}

/* ============================================================
 * 4. PROCEDURAL GENERATION
 * ============================================================ */

function mulberry32(a) {
    return function () {
        a |= 0; a = a + 0x6D2B79F5 | 0;
        let t = Math.imul(a ^ a >>> 15, 1 | a);
        t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
        return ((t ^ t >>> 14) >>> 0) / 4294967296;
    };
}

function degToMidi(rootPc, scale, deg, octShift) {
    const n = scale.length;
    const o = Math.floor(deg / n) + (octShift || 0);
    const i = ((deg % n) + n) % n;
    return (o + 1) * 12 + rootPc + scale[i];
}

function generateSong(seed) {
    const rnd = mulberry32(seed >>> 0);
    const pick = arr => arr[Math.floor(rnd() * arr.length)];

    const keyPc = pick(KEY_PCS);
    const scale = pick(SCALES);
    const prog  = pick(PROGS);
    const tempo = 70 + Math.floor(rnd() * 91);

    const beatsPerChordBase = pick([2,2,4,4,4,8]);
    const feel = pick(['straight','straight','straight','swing','shuffle','halftime']);
    const swingAmt = feel === 'swing' ? 0.14 + rnd() * 0.10
                   : feel === 'shuffle' ? 0.24 + rnd() * 0.12 : 0;

    const padWave  = pick(PAD_WAVES);
    const bassWave = pick(BASS_WAVES);
    const subWave  = pick(SUB_WAVES);
    const leadWave = pick(LEAD_WAVES);
    const arpWave  = pick(ARP_WAVES);
    const gtrWave  = pick(GTR_WAVES);

    const profile = {
        padVol:    0.20 + rnd() * 0.24,
        guitarVol: 0.22 + rnd() * 0.28,
        bassVol:   0.32 + rnd() * 0.24,
        arpVol:    0.10 + rnd() * 0.20,
        synthVol:  0.16 + rnd() * 0.20,
        kickVol:   0.60 + rnd() * 0.28,
        snareVol:  0.45 + rnd() * 0.26,
        hatVol:    0.18 + rnd() * 0.20,
        guitarBright: 2500 + rnd() * 11000,
        guitarDark:   280 + rnd() * 1200,
        synthBright:  1500 + rnd() * 6500,
        synthDark:    500 + rnd() * 1900,
        padBright:    1100 + rnd() * 4800,
        bassBright:   220 + rnd() * 580,
        hatSkip: rnd() < 0.5 ? 0 : (0.25 + rnd() * 0.35)
    };

    const form = pick(SONG_FORMS);
    const sections = [];
    let currentBar = 0;
    for (const name of form) {
        const cfg = SECTION_CONFIG[name];
        const bars = pick(cfg.bars);
        sections.push({
            name, startBar: currentBar, bars,
            energy: cfg.energy, parts: cfg.parts,
            velScale: cfg.velScale, octaveShift: cfg.octaveShift || 0,
            build: !!cfg.build, crash: !!cfg.crash
        });
        currentBar += bars;
    }
    const totalBars = currentBar;
    const totalBeats = totalBars * 4;

    const events = [];
    let chordIdx = 0;

    function chordBeatsFor(sec) {
        if (sec.name === 'intro' || sec.name === 'outro') return Math.max(4, beatsPerChordBase);
        if (sec.name === 'bridge' || sec.name === 'breakdown') return Math.max(4, beatsPerChordBase);
        if (sec.name === 'chorus' || sec.name === 'drop') return Math.max(2, Math.min(4, beatsPerChordBase));
        return beatsPerChordBase;
    }

    for (let si = 0; si < sections.length; si++) {
        const sec = sections[si];
        const secStartBeat = sec.startBar * 4;
        const secBeats = sec.bars * 4;
        const beatsPerChord = chordBeatsFor(sec);

        if (sec.crash) {
            events.push({ time: secStartBeat, kind: 'crash',
                          velocity: 0.55 * sec.velScale });
        }

        for (let b = 0; b < secBeats; b++) {
            const absBeat = secStartBeat + b;
            const beatInBar = b % 4;

            if (b % beatsPerChord === 0) {
                const deg = prog[chordIdx % prog.length];

                if (sec.parts.indexOf('pad') >= 0) {
                    events.push({
                        time: absBeat, duration: beatsPerChord, kind: 'pad',
                        notes: [
                            degToMidi(keyPc, scale, deg,     0),
                            degToMidi(keyPc, scale, deg + 2, 0),
                            degToMidi(keyPc, scale, deg + 4, 0)
                        ],
                        velocity: profile.padVol * sec.velScale,
                        octaveShift: sec.octaveShift
                    });
                }

                if (sec.parts.indexOf('guitar') >= 0) {
                    const gNotes = [
                        degToMidi(keyPc, scale, deg,     0),
                        degToMidi(keyPc, scale, deg + 2, 0),
                        degToMidi(keyPc, scale, deg + 4, 0),
                        degToMidi(keyPc, scale, deg + 7, 1)
                    ];
                    events.push({
                        time: absBeat, duration: 0.9, kind: 'guitar',
                        notes: gNotes,
                        velocity: profile.guitarVol * sec.velScale,
                        octaveShift: sec.octaveShift
                    });
                    if (rnd() < 0.55 && beatsPerChord > 1) {
                        events.push({
                            time: absBeat + 1, duration: 0.4, kind: 'guitar',
                            notes: [gNotes[0], gNotes[2]],
                            velocity: profile.guitarVol * 0.6 * sec.velScale,
                            octaveShift: sec.octaveShift
                        });
                    }
                }

                if (sec.parts.indexOf('bass') >= 0) {
                    events.push({
                        time: absBeat, duration: 0.9, kind: 'bass',
                        notes: [degToMidi(keyPc, scale, deg, 0)],
                        velocity: profile.bassVol * sec.velScale,
                        octaveShift: sec.octaveShift
                    });
                }
                chordIdx++;
            }

            if (sec.parts.indexOf('drums') >= 0) {
                if (feel === 'halftime') {
                    if (beatInBar === 0) events.push({ time: absBeat, kind: 'kick', velocity: profile.kickVol * sec.velScale });
                } else {
                    if (beatInBar === 0 || beatInBar === 2) events.push({ time: absBeat, kind: 'kick', velocity: profile.kickVol * sec.velScale });
                    if (rnd() < 0.28) events.push({ time: absBeat + 0.75, kind: 'kick', velocity: profile.kickVol * 0.5 * sec.velScale });
                }
                if (feel === 'halftime') {
                    if (beatInBar === 2) events.push({ time: absBeat, kind: 'snare', velocity: profile.snareVol * sec.velScale });
                } else {
                    if (beatInBar === 1 || beatInBar === 3) events.push({ time: absBeat, kind: 'snare', velocity: profile.snareVol * sec.velScale });
                }
                const hatDensity = 0.55 + sec.energy * 0.45;
                if (rnd() < hatDensity) events.push({ time: absBeat, kind: 'hat', velocity: profile.hatVol * sec.velScale });
                if (rnd() < hatDensity * 0.8) events.push({ time: absBeat + 0.5, kind: 'hat', velocity: profile.hatVol * 0.65 * sec.velScale });
            }

            if (sec.parts.indexOf('bass') >= 0) {
                const deg = prog[(chordIdx - 1) % prog.length];
                events.push({
                    time: absBeat + 0.5, duration: 0.4, kind: 'bass',
                    notes: [degToMidi(keyPc, scale, deg, 0)],
                    velocity: profile.bassVol * 0.7 * sec.velScale,
                    octaveShift: sec.octaveShift
                });
            }

            if (sec.parts.indexOf('arp') >= 0) {
                const deg = prog[(chordIdx - 1) % prog.length];
                const pool = [
                    degToMidi(keyPc, scale, deg,     0),
                    degToMidi(keyPc, scale, deg + 2, 0),
                    degToMidi(keyPc, scale, deg + 4, 0),
                    degToMidi(keyPc, scale, deg + 7, 0),
                    degToMidi(keyPc, scale, deg + 9, 0)
                ];
                const density = 0.25 + sec.energy * 0.55;
                for (let s = 0; s < 4; s++) {
                    if (rnd() > density) continue;
                    events.push({
                        time: absBeat + s * 0.25, duration: 0.2, kind: 'arp',
                        notes: [pool[(s + si) % pool.length]],
                        velocity: profile.arpVol * sec.velScale,
                        octaveShift: sec.octaveShift
                    });
                }
            }

            if (sec.parts.indexOf('lead') >= 0) {
                if (rnd() < 0.10 * sec.energy) {
                    const deg = prog[(chordIdx - 1) % prog.length] + 7 + Math.floor(rnd() * 4);
                    events.push({
                        time: absBeat + 0.5,
                        duration: 0.5 + rnd() * 0.6,
                        kind: 'synth',
                        notes: [degToMidi(keyPc, scale, deg, 0)],
                        velocity: profile.synthVol * sec.velScale,
                        octaveShift: sec.octaveShift
                    });
                }
            }
        }
    }

    if (swingAmt > 0) {
        for (const ev of events) {
            if (ev.kind === 'hat' && Math.abs((ev.time % 1) - 0.5) < 0.01) {
                ev.time += swingAmt;
            }
        }
    }

    events.sort((a, b) => a.time - b.time);

    const lastEv = events[events.length - 1];
    const eventsEndBeat = lastEv ? lastEv.time + 4 : totalBeats;
    const songBeats = Math.max(totalBeats + 2, eventsEndBeat + 2);

    const song = {
        source: 'procedural',
        tempo, bars: totalBars, beatsPerBar: 4,
        totalBeats: songBeats, events, seed,
        profile, sections, scale, prog, keyPc,
        timbres: { padWave, bassWave, subWave, leadWave, arpWave, gtrWave },
        feel, swingAmt,
        hasLyrics: false
    };

    addVocalLayer(song, seed);

    return song;
}

/* ============================================================
 * 4b. VOCAL LAYER (procedural)
 * ============================================================ */

const VOCAL_POOLS = {
    bright: [
        ['L','AA'], ['N','AA'], ['M','AA'], ['S','AA'],
        ['L','AY'], ['N','AY'], ['M','AY'], ['SH','AY'],
        ['F','EY'], ['D','EY'], ['N','EY'], ['L','EY']
    ],
    warm: [
        ['L','OW'], ['N','OW'], ['M','OW'], ['D','OW'],
        ['S','OW'], ['R','OW'], ['T','OW'],
        ['B','EH'], ['N','EH'], ['M','EH'], ['S','EH']
    ],
    dark: [
        ['L','UW'], ['N','UW'], ['M','UW'], ['D','UW'],
        ['K','UW'], ['S','UW'],
        ['L','UH'], ['N','UH'], ['B','UH']
    ]
};

const VOCAL_SIGNATURES = [
    { name: 'ChillSpace',         syllables: [['CH','IH','L'], ['S','P','EY','S']] },
    { name: 'love',               syllables: [['L','AH','V']] },
    { name: 'quit social media',  syllables: [
        ['K','W','IH','T'], ['S','OW'], ['SH','AH','L'],
        ['M','IY'], ['D','IY'], ['AH']
    ]}
];

function sectionForBeat(song, beat) {
    for (const s of song.sections || []) {
        const start = s.startBar * 4;
        if (beat >= start && beat < start + s.bars * 4) return s;
    }
    return null;
}

function poolForSection(sec) {
    if (!sec) return VOCAL_POOLS.warm;
    if (sec.name === 'chorus' || sec.name === 'drop') return VOCAL_POOLS.bright;
    if (sec.name === 'bridge' || sec.name === 'breakdown') return VOCAL_POOLS.dark;
    return VOCAL_POOLS.warm;
}

function attachRate(ev, sec) {
    if (ev.kind === 'synth') return 1.0;
    if (!sec) return 0.20;
    if (sec.name === 'chorus' || sec.name === 'drop') return 0.35;
    if (sec.name === 'bridge' || sec.name === 'breakdown') return 0.30;
    return 0.22;
}

function placePhrase(melodyEvents, syllables, rnd, usedSet) {
    const n = syllables.length;
    if (melodyEvents.length < n) return null;

    const candidates = [];
    for (let i = 0; i + n <= melodyEvents.length; i++) {
        const win = melodyEvents.slice(i, i + n);
        let overlap = false;
        for (const e of win) if (usedSet.has(e)) { overlap = true; break; }
        if (overlap) continue;
        const span = win[n - 1].time - win[0].time;
        if (span > n * 1.5) continue;
        candidates.push(i);
    }
    if (candidates.length === 0) return null;

    const start = candidates[Math.floor(rnd() * candidates.length)];
    const used = [];
    for (let k = 0; k < n; k++) {
        const ev = melodyEvents[start + k];
        ev.lyric = syllables[k].slice();
        usedSet.add(ev);
        used.push(ev);
    }
    return used;
}

function forcePlacePhrase(melodyEvents, syllables, rnd, usedSet) {
    const n = syllables.length;
    if (melodyEvents.length < n) return null;

    for (let attempt = 0; attempt < 60; attempt++) {
        const start = Math.floor(rnd() * (melodyEvents.length - n + 1));
        const win = melodyEvents.slice(start, start + n);
        let overlap = false;
        for (const e of win) if (usedSet.has(e)) { overlap = true; break; }
        if (overlap) continue;
        for (let k = 0; k < n; k++) {
            win[k].lyric = syllables[k].slice();
            usedSet.add(win[k]);
        }
        return win;
    }
    return null;
}

function addVocalLayer(song, seed) {
    if (!song || !song.events) return;

    const rnd = mulberry32((seed ^ 0x5EED) >>> 0);
    const pick = arr => arr[Math.floor(rnd() * arr.length)];

    const melodyEvents = song.events.filter(e =>
        (e.kind === 'synth' || e.kind === 'arp') &&
        e.notes && e.notes.length
    );
    melodyEvents.sort((a, b) => a.time - b.time);
    if (melodyEvents.length === 0) return;

    for (const ev of melodyEvents) {
        const sec = sectionForBeat(song, ev.time);
        if (rnd() < attachRate(ev, sec)) {
            ev.lyric = pick(poolForSection(sec)).slice();
        }
    }

    const usedSet = new Set();
    const placed = [];

    for (const phrase of VOCAL_SIGNATURES) {
        let result = placePhrase(melodyEvents, phrase.syllables, rnd, usedSet);
        if (!result) {
            result = forcePlacePhrase(melodyEvents, phrase.syllables, rnd, usedSet);
        }
        if (result) {
            placed.push({ name: phrase.name, at: result[0].time });
        } else {
            console.warn('[Vocal] could not place phrase:', phrase.name);
        }
    }

    if (placed.length) {
        console.log('[Vocal] signature phrases:',
            placed.map(p => p.name + ' @beat ' + p.at.toFixed(1)).join(', '));
    }

    song.hasLyrics = melodyEvents.some(e => e.lyric);
    song.vocalPhrases = placed;
}

/* ============================================================
 * 5. KLATTSCH VOCAL INTEGRATION
 * ============================================================ */

const VOCAL_CFG = {
    enabled: true,
    bundleGlobal: 'Klattsch',
    workletFile: 'formant-worklet.js',
    processorName: 'formant-processor',
    messageType: 'schedule',
    gain: 0.32,
    transpose: 12
};

const VOCAL = {
    ready: false,
    failed: false,
    node: null,
    gain: null,
    firstLog: false
};

async function initVocal(ctx, dest) {
    if (VOCAL.ready || VOCAL.failed) return;
    if (!VOCAL_CFG.enabled) { VOCAL.failed = true; return; }

    const K = global[VOCAL_CFG.bundleGlobal];
    if (!K || typeof K.compileString !== 'function') {
        console.warn('[Vocal] klattsch not loaded (need klattsch.bundle.js before music-engine.js)');
        VOCAL.failed = true;
        return;
    }

    try {
        let code = '';
        if (typeof window !== 'undefined' && window.AndroidUtils && typeof window.AndroidUtils.loadAsset === 'function') {
            code = window.AndroidUtils.loadAsset(VOCAL_CFG.workletFile);
        }
        if (!code) {
            const res = await fetch(VOCAL_CFG.workletFile);
            code = await res.text();
        }
        const blob = new Blob([code], { type: 'application/javascript' });
        const blobUrl = URL.createObjectURL(blob);
        await ctx.audioWorklet.addModule(blobUrl);
        URL.revokeObjectURL(blobUrl);
    } catch (e) {
        console.warn('[Vocal] Blob addModule failed:', e.message);
        VOCAL.failed = true;
        return;
    }

    let node;
    try {
        node = new AudioWorkletNode(ctx, VOCAL_CFG.processorName);
    } catch (e) {
        console.warn('[Vocal] AudioWorkletNode failed:', e.message);
        VOCAL.failed = true;
        return;
    }

    const g = ctx.createGain();
    g.gain.value = VOCAL_CFG.gain;
    node.connect(g);
    g.connect(dest);

    VOCAL.node = node;
    VOCAL.gain = g;
    VOCAL.ready = true;
    console.log('[Vocal] ready — klattsch connected');
}

/* Build a klattsch directive string. The first lyric event is the
   clock zero — no leading rest. sendVocal schedules the post so
   the message arrives exactly when the first lyric should sound. */
function buildVocalDirective(song) {
    const lyricEvents = (song.events || []).filter(e => e.lyric && e.lyric.length);
    if (!lyricEvents.length) return null;

    const spb = 60 / song.tempo;
    const firstLyricMs = lyricEvents[0].time * spb * 1000;

    let out = '';
    let lastStartMs = 0;
    let lastEstMs = 120;

    for (let i = 0; i < lyricEvents.length; i++) {
        const ev = lyricEvents[i];
        const noteStartMs = ev.time * spb * 1000 - firstLyricMs;

        if (i > 0) {
            const gap = noteStartMs - lastStartMs;
            const rest = Math.max(0, gap - lastEstMs);
            if (rest > 25) out += 'r' + Math.round(rest) + ' ';
        }

        const midi = ev.notes[0] + (ev.octaveShift || 0) * 12 + VOCAL_CFG.transpose;
        out += 'b' + midiToNoteName(midi) + ' ';
        out += '( ' + ev.lyric.join(' ') + ' ) ';

        lastEstMs = 60 + ev.lyric.length * 80;
        lastStartMs = noteStartMs;
    }

    return out.trim();
}

function sendVocal(song) {
    if (!VOCAL.ready) return false;
    if (!song.hasLyrics) return false;

    const directive = buildVocalDirective(song);
    if (!directive) return false;

    let result;
    try {
        result = global[VOCAL_CFG.bundleGlobal].compileString(directive);
    } catch (e) {
        console.warn('[Vocal] compileString threw:', e);
        return false;
    }

    const schedule = (result && (result.schedule || result)) || [];

    if (!VOCAL.firstLog) {
        VOCAL.firstLog = true;
        console.log('[Vocal] directive (first 200 chars):', directive.slice(0, 200));
        console.log('[Vocal] schedule length:', schedule.length);
    }

    try {
        VOCAL.node.port.postMessage({ type: VOCAL_CFG.messageType, schedule });
    } catch (e) {
        console.warn('[Vocal] postMessage failed:', e);
        return false;
    }
    return true;
}

/* ============================================================
 * 6. AUDIO PLAYBACK
 * ============================================================ */

const midiToFreq = m => 440 * Math.pow(2, (m - 69) / 12);

class LoopPlayer {
    constructor(ctx, dest, song, noLoop) {
        this.ctx = ctx;
        this.dest = dest;
        this.song = song;
        this.profile = song.profile || {};
        this.timbres = song.timbres || {};
        this.secPerBeat = 60 / song.tempo;
        this.loopSeconds = song.totalBeats * this.secPerBeat;
        this.out = ctx.createGain();
        this.out.gain.value = 0;
        this.out.connect(dest);
        this.running = false;
        this.timer = null;
        this.loopStartTime = 0;
        this.loopIteration = 0;
        this.eventIndex = 0;
        this.sources = new Set();
        this.noLoop = !!noLoop;

        const nSamples = Math.floor(ctx.sampleRate * 0.5);
        this.noiseBuffer = ctx.createBuffer(1, nSamples, ctx.sampleRate);
        const nd = this.noiseBuffer.getChannelData(0);
        for (let i = 0; i < nSamples; i++) nd[i] = Math.random() * 2 - 1;
    }

    start(when, fadeIn) {
        this.running = true;
        this.loopStartTime = when;
        this.loopIteration = 0;
        this.eventIndex = 0;
        const g = this.out.gain;
        g.cancelScheduledValues(when);
        g.setValueAtTime(0.0001, when);
        g.exponentialRampToValueAtTime(1, when + (fadeIn || 0.5));
        this.timer = setInterval(() => this.tick(), 25);
        this.tick();
    }

    tick() {
        if (!this.running) return;
        const ctx = this.ctx;
        const horizon = ctx.currentTime + 0.35;
        let guard = 0;
        while (guard++ < 1500) {
            if (this.eventIndex >= this.song.events.length) {
                if (this.noLoop) { this._finish(); return; }
                this.eventIndex = 0;
                this.loopIteration++;
                continue;
            }
            const ev = this.song.events[this.eventIndex];
            const absTime = this.loopStartTime
                          + this.loopIteration * this.loopSeconds
                          + ev.time * this.secPerBeat;
            if (absTime > horizon) break;
            if (absTime >= ctx.currentTime - 0.02) this.schedule(ev, absTime);
            this.eventIndex++;
        }
    }

    _finish() {
        this.running = false;
        if (this.timer) { clearInterval(this.timer); this.timer = null; }
        setTimeout(() => {
            for (const src of this.sources) { try { src.stop(); } catch (e) {} }
            this.sources.clear();
            try { this.out.disconnect(); } catch (e) {}
        }, 2000);
    }

    schedule(ev, when) {
        switch (ev.kind) {
            case 'kick':   this.kick(ev, when);   break;
            case 'snare':  this.snare(ev, when);  break;
            case 'hat':    this.hat(ev, when);    break;
            case 'crash':  this.crash(ev, when);  break;
            case 'bass':   this.bass(ev, when);   break;
            case 'pad':    this.pad(ev, when);    break;
            case 'guitar': this.guitar(ev, when); break;
            case 'arp':    this.arp(ev, when);    break;
            case 'synth':  this.synth(ev, when);  break;
            case 'piano':  this.piano(ev, when);  break;
            case 'bell':   this.bell(ev, when);   break;
        }
    }

    _track(node, stopAt) {
        this.sources.add(node);
        const ms = Math.max(0, (stopAt - this.ctx.currentTime + 0.5) * 1000);
        setTimeout(() => this.sources.delete(node), ms);
    }

    _shift(midi, octaveShift) { return midi + (octaveShift || 0) * 12; }

    kick(ev, t) {
        const ctx = this.ctx;
        const osc = ctx.createOscillator();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(160, t);
        osc.frequency.exponentialRampToValueAtTime(42, t + 0.09);
        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(ev.velocity, t + 0.003);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.32);
        osc.connect(g).connect(this.out);
        osc.start(t); osc.stop(t + 0.4);
        this._track(osc, t + 0.4);
    }

    snare(ev, t) {
        const ctx = this.ctx;
        const noise = ctx.createBufferSource();
        noise.buffer = this.noiseBuffer;
        const nf = ctx.createBiquadFilter();
        nf.type = 'highpass'; nf.frequency.value = 1800;
        const ng = ctx.createGain();
        ng.gain.setValueAtTime(ev.velocity * 0.75, t);
        ng.gain.exponentialRampToValueAtTime(0.0001, t + 0.16);
        noise.connect(nf).connect(ng).connect(this.out);
        noise.start(t); noise.stop(t + 0.25);

        const body = ctx.createOscillator();
        body.type = 'triangle';
        body.frequency.setValueAtTime(220, t);
        body.frequency.exponentialRampToValueAtTime(140, t + 0.1);
        const bg = ctx.createGain();
        bg.gain.setValueAtTime(ev.velocity * 0.45, t);
        bg.gain.exponentialRampToValueAtTime(0.0001, t + 0.12);
        body.connect(bg).connect(this.out);
        body.start(t); body.stop(t + 0.16);

        this._track(noise, t + 0.3);
        this._track(body, t + 0.2);
    }

    hat(ev, t) {
        const ctx = this.ctx;
        const noise = ctx.createBufferSource();
        noise.buffer = this.noiseBuffer;
        const f = ctx.createBiquadFilter();
        f.type = 'highpass'; f.frequency.value = 8000;
        const g = ctx.createGain();
        g.gain.setValueAtTime(ev.velocity * 0.18, t);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.06);
        noise.connect(f).connect(g).connect(this.out);
        noise.start(t); noise.stop(t + 0.1);
        this._track(noise, t + 0.15);
    }

    crash(ev, t) {
        const ctx = this.ctx;
        const noise = ctx.createBufferSource();
        noise.buffer = this.noiseBuffer;
        noise.loop = true;
        const f = ctx.createBiquadFilter();
        f.type = 'highpass'; f.frequency.value = 4000;
        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(ev.velocity, t + 0.005);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 1.6);
        noise.connect(f).connect(g).connect(this.out);
        noise.start(t); noise.stop(t + 1.8);
        this._track(noise, t + 1.8);
    }

    bass(ev, t) {
        const ctx = this.ctx, p = this.profile;
        const dur = ev.duration * this.secPerBeat;
        const endTime = t + dur + 0.2;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        const peak = ev.velocity * 0.55;
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(peak, t + 0.008);
        g.gain.setTargetAtTime(0.0001, t + dur * 0.4, 0.16);

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass'; lp.Q.value = 4.5;
        const bright = p.bassBright || 500;
        lp.frequency.setValueAtTime(bright * 0.4, t);
        lp.frequency.exponentialRampToValueAtTime(bright * 2.2, t + 0.05);
        lp.frequency.exponentialRampToValueAtTime(bright, t + dur);
        lp.connect(g).connect(this.out);

        const bw = this.timbres.bassWave || 'sawtooth';
        const sw = this.timbres.subWave  || 'sine';

        ev.notes.forEach(mRaw => {
            const f = midiToFreq(this._shift(mRaw, oct));
            const saw = ctx.createOscillator();
            saw.type = bw; saw.frequency.value = f;
            saw.connect(lp); saw.start(t); saw.stop(endTime);
            this._track(saw, endTime);

            const sub = ctx.createOscillator();
            sub.type = sw; sub.frequency.value = f / 2;
            const sg = ctx.createGain(); sg.gain.value = 0.6;
            sub.connect(sg).connect(lp);
            sub.start(t); sub.stop(endTime);
            this._track(sub, endTime);
        });
    }

    pad(ev, t) {
        const ctx = this.ctx, p = this.profile;
        const pw = this.timbres.padWave || 'sawtooth';
        const dur = ev.duration * this.secPerBeat;
        const attack = 0.18, release = 0.7;
        const endTime = t + dur + release + 0.2;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        const peak = ev.velocity * 0.16;
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(peak, t + attack);
        g.gain.setValueAtTime(peak * 0.75, t + dur * 0.5);
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur + release);

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass';
        lp.frequency.value = p.padBright || 3500;
        lp.Q.value = 0.7;
        lp.connect(g).connect(this.out);

        ev.notes.forEach((mRaw, i) => {
            const f = midiToFreq(this._shift(mRaw, oct));
            [-9, 0, 9].forEach(dt => {
                const osc = ctx.createOscillator();
                osc.type = pw; osc.frequency.value = f;
                osc.detune.value = dt + i * 2;
                osc.connect(lp); osc.start(t); osc.stop(endTime);
                this._track(osc, endTime);
            });
        });
    }

    guitar(ev, t) {
        const ctx = this.ctx, p = this.profile;
        const gw = this.timbres.gtrWave || 'sawtooth';
        const dur = ev.duration * this.secPerBeat;
        const decay = 0.7 + dur * 1.1;
        const endTime = t + decay + 0.15;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(ev.velocity * 0.32, t + 0.005);
        g.gain.exponentialRampToValueAtTime(0.0001, t + decay);

        const body = ctx.createBiquadFilter();
        body.type = 'peaking'; body.frequency.value = 380;
        body.Q.value = 2; body.gain.value = 6;

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass'; lp.Q.value = 1.8;
        lp.frequency.setValueAtTime(p.guitarBright || 8000, t);
        lp.frequency.exponentialRampToValueAtTime(p.guitarDark || 600, t + decay);
        lp.connect(body).connect(g).connect(this.out);

        ev.notes.forEach(mRaw => {
            const f = midiToFreq(this._shift(mRaw, oct));
            [0, 5].forEach(dt => {
                const osc = ctx.createOscillator();
                osc.type = gw; osc.frequency.value = f; osc.detune.value = dt;
                osc.connect(lp); osc.start(t); osc.stop(endTime);
                this._track(osc, endTime);
            });
        });
    }

    arp(ev, t) {
        const ctx = this.ctx;
        const aw = this.timbres.arpWave || 'triangle';
        const dur = ev.duration * this.secPerBeat;
        const decay = 0.22 + dur * 0.7;
        const endTime = t + decay + 0.06;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(ev.velocity * 0.28, t + 0.004);
        g.gain.exponentialRampToValueAtTime(0.0001, t + decay);

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass'; lp.Q.value = 1.4;
        lp.frequency.setValueAtTime(7000, t);
        lp.frequency.exponentialRampToValueAtTime(1800, t + decay);
        lp.connect(g).connect(this.out);

        ev.notes.forEach(mRaw => {
            const osc = ctx.createOscillator();
            osc.type = aw;
            osc.frequency.value = midiToFreq(this._shift(mRaw, oct));
            osc.connect(lp); osc.start(t); osc.stop(endTime);
            this._track(osc, endTime);
        });
    }

    synth(ev, t) {
        const ctx = this.ctx, p = this.profile;
        const lw = this.timbres.leadWave || 'sawtooth';
        const dur = ev.duration * this.secPerBeat;
        const attack = 0.025, release = 0.35;
        const endTime = t + dur + release + 0.1;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        const peak = ev.velocity * 0.3;
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(peak, t + attack);
        g.gain.setValueAtTime(peak * 0.82, t + dur * 0.55);
        g.gain.exponentialRampToValueAtTime(0.0001, t + dur + release);

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass'; lp.Q.value = 3;
        lp.frequency.setValueAtTime(500, t);
        lp.frequency.exponentialRampToValueAtTime(p.synthBright || 4000, t + 0.09);
        lp.frequency.exponentialRampToValueAtTime(p.synthDark || 1400, t + dur + release);
        lp.connect(g).connect(this.out);

        ev.notes.forEach(mRaw => {
            const f = midiToFreq(this._shift(mRaw, oct));
            [-7, 7].forEach(dt => {
                const osc = ctx.createOscillator();
                osc.type = lw; osc.frequency.value = f; osc.detune.value = dt;
                osc.connect(lp); osc.start(t); osc.stop(endTime);
                this._track(osc, endTime);
            });
        });
    }

    piano(ev, t) {
        const ctx = this.ctx;
        const dur = ev.duration * this.secPerBeat;
        const decay = 0.9 + Math.min(dur, 2.5) * 0.6;
        const endTime = t + decay + 0.15;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(ev.velocity * 0.30, t + 0.006);
        g.gain.exponentialRampToValueAtTime(0.0001, t + decay);

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass'; lp.Q.value = 1.2;
        const f0 = midiToFreq(this._shift(ev.notes[0] || 60, oct));
        lp.frequency.setValueAtTime(Math.min(7000, f0 * 8), t);
        lp.frequency.exponentialRampToValueAtTime(Math.max(500, f0 * 1.5), t + decay);
        lp.connect(g).connect(this.out);

        ev.notes.forEach(mRaw => {
            const f = midiToFreq(this._shift(mRaw, oct));
            const o1 = ctx.createOscillator();
            o1.type = 'triangle'; o1.frequency.value = f;
            o1.connect(lp); o1.start(t); o1.stop(endTime);
            this._track(o1, endTime);

            const o2 = ctx.createOscillator();
            o2.type = 'sine'; o2.frequency.value = f * 2;
            const g2 = ctx.createGain(); g2.gain.value = 0.35;
            o2.connect(g2).connect(lp);
            o2.start(t); o2.stop(endTime);
            this._track(o2, endTime);
        });
    }

    bell(ev, t) {
        const ctx = this.ctx;
        const dur = ev.duration * this.secPerBeat;
        const decay = 0.5 + dur * 1.4;
        const endTime = t + decay + 0.2;
        const oct = ev.octaveShift || 0;

        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(ev.velocity * 0.28, t + 0.006);
        g.gain.exponentialRampToValueAtTime(0.0001, t + decay);

        const lp = ctx.createBiquadFilter();
        lp.type = 'lowpass'; lp.Q.value = 0.8;
        lp.frequency.value = 5500;
        lp.connect(g).connect(this.out);

        ev.notes.forEach(mRaw => {
            const f = midiToFreq(this._shift(mRaw, oct));
            const o1 = ctx.createOscillator();
            o1.type = 'sine'; o1.frequency.value = f;
            o1.connect(lp); o1.start(t); o1.stop(endTime);
            this._track(o1, endTime);

            const o2 = ctx.createOscillator();
            o2.type = 'sine'; o2.frequency.value = f * 2.01;
            const g2 = ctx.createGain(); o2.connect(g2).connect(lp);
            g2.gain.setValueAtTime(ev.velocity * 0.08, t);
            g2.gain.exponentialRampToValueAtTime(0.0001, t + decay * 0.6);
            o2.start(t); o2.stop(endTime);
            this._track(o2, endTime);
        });
    }

    fadeOutAndStop(dur) {
        if (!this.running) return;
        const now = this.ctx.currentTime;
        const g = this.out.gain;
        g.cancelScheduledValues(now);
        g.setValueAtTime(Math.max(0.0001, g.value), now);
        g.exponentialRampToValueAtTime(0.0001, now + dur);
        this.running = false;
        const myTimer = this.timer;
        this.timer = null;
        setTimeout(() => {
            if (myTimer) clearInterval(myTimer);
            for (const src of this.sources) { try { src.stop(); } catch (e) {} }
            this.sources.clear();
            try { this.out.disconnect(); } catch (e) {}
        }, dur * 1000 + 150);
    }
}

/* ============================================================
 * 7. MUSIC MANAGER (public API)
 * ============================================================ */

class MusicManager {
    constructor() {
        this.ctx = null;
        this.master = null;
        this.voiceGain = null;
        this.current = null;
        this.currentSeed = null;
        this.volume = 0.5;
    }

    ensureContext() {
        if (this.ctx) {
            if (this.ctx.state === 'suspended') {
                try { this.ctx.resume(); } catch (e) {}
            }
            return this.ctx;
        }
        const Ctor = window.AudioContext || window.webkitAudioContext;
        if (!Ctor) return null;
        this.ctx = new Ctor();

        this.voiceGain = this.ctx.createGain();
        this.voiceGain.gain.value = 1;

        this.master = this.ctx.createGain();
        this.master.gain.value = this.volume;

        const comp = this.ctx.createDynamicsCompressor();
        comp.threshold.value = -12;
        comp.knee.value = 22;
        comp.ratio.value = 6;
        comp.attack.value = 0.004;
        comp.release.value = 0.18;

        this.voiceGain.connect(this.master);
        this.master.connect(comp);
        comp.connect(this.ctx.destination);

        initVocal(this.ctx, this.voiceGain);

        return this.ctx;
    }

    getContext() { return this.ensureContext(); }

    generateSong(seed) { return generateSong(seed >>> 0); }

    loadXml(xmlText) { return parseSongXml(xmlText); }

    unlock() {
        const ctx = this.ensureContext();
        if (ctx && ctx.state === 'suspended') {
            try { ctx.resume(); } catch (e) {}
        }
        return ctx;
    }

    playSong(song, when, noLoop) {
        if (!song) return null;
        const ctx = this.ensureContext();
        if (!ctx) return null;

        const old = this.current;
        const player = new LoopPlayer(ctx, this.voiceGain, song, noLoop);
        this.current = player;
        this.currentSeed = song.seed || null;

        if (old) old.fadeOutAndStop(0.35);

        const startAt = (typeof when === 'number') ? when : ctx.currentTime + 0.05;
        player.start(startAt, 0.35);

        if (VOCAL.ready && song.hasLyrics && noLoop) {
            const spb = 60 / song.tempo;
            const firstLyricEvent = song.events.find(e => e.lyric);
            const firstLyricSec = firstLyricEvent ? firstLyricEvent.time * spb : 0;

            const delayMs = Math.max(0, (startAt - ctx.currentTime + firstLyricSec) * 1000);
            console.log('[Vocal] scheduling for +' + Math.round(delayMs) + 'ms');

            setTimeout(() => sendVocal(song), delayMs);
        }

        return startAt;
    }

    stop(fade) {
        if (this.current) {
            this.current.fadeOutAndStop(fade == null ? 0.4 : fade);
            this.current = null;
            this.currentSeed = null;
        }
    }

    pause() {
        if (this.ctx && this.ctx.state === 'running') {
            try { this.ctx.suspend(); } catch (e) {}
        }
    }

    resume() {
        if (this.ctx && this.ctx.state === 'suspended') {
            try { this.ctx.resume(); } catch (e) {}
        }
    }

    setVolume(v) {
        this.volume = Math.max(0, Math.min(1, v));
        if (this.master && this.ctx) {
            const now = this.ctx.currentTime;
            this.master.gain.cancelScheduledValues(now);
            this.master.gain.setTargetAtTime(this.volume, now, 0.05);
        }
    }

    vocalStatus() {
        return { ready: VOCAL.ready, failed: VOCAL.failed };
    }
}

global.ChillSpaceMusic = new MusicManager();

})(typeof window !== 'undefined' ? window : this);