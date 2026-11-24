#!/usr/bin/env bash
# Builds the Hi-Res test corpus and manifest.csv. Needs ffmpeg (with libsoxr and libmp3lame), python3, numpy, scipy.
# Usage: make-corpus.sh [output-dir]   Every source is synthetic, so provenance is known by construction.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-${TMPDIR:-/tmp}/specty-hires-corpus}"
mkdir -p "$OUT/base" "$OUT/files"
MAN="$OUT/manifest.csv"
echo "id,truth,hires,rate,bits,detail" > "$MAN"
FF=(ffmpeg -hide_banner -loglevel error -y -nostdin)
F24=(-c:a flac -sample_fmt s32 -bits_per_raw_sample 24)
F16=(-c:a flac -sample_fmt s16)

emit() { echo "$1,$2,$3,$4,$5,$6" >> "$MAN"; }
file() { echo "$OUT/files/$1.flac"; }
base() { [ -f "$OUT/base/$1.wav" ] || python3 "$HERE/synth.py" "$OUT/base/$1.wav" --kind "$2" "${@:3}"; }
rs() { # resampler filter: rate resampler [dither]
  local f="aresample=$1"
  [ "$2" = soxr ] && f+=":resampler=soxr:precision=28"
  [ -n "${3:-}" ] && f+=":dither_method=triangular"
  echo "$f"
}

base dark dark
base bright bright
base sporadic sporadic
base dsd dsd --rate 96000 --floor-rate 96000

# Native lossless at several rates (truth: real content, 24-bit floor).
for r in 44100 48000; do
  "${FF[@]}" -i "$OUT/base/dark.wav" -af "$(rs $r soxr)" "${F24[@]}" "$(file native_${r}_dark_24)"
  emit native_${r}_dark_24 NATIVE_FULL na $r 24 "dark tilt, soxr from 192k"
done
"${FF[@]}" -i "$OUT/base/bright.wav" -af "$(rs 44100 soxr)" "${F24[@]}" "$(file native_44100_bright_24)"
emit native_44100_bright_24 NATIVE_FULL na 44100 24 "bright tilt"
for k in dark bright sporadic; do
  "${FF[@]}" -i "$OUT/base/$k.wav" -af "$(rs 96000 soxr)" "${F24[@]}" "$(file native_96000_${k}_24)"
  emit native_96000_${k}_24 NATIVE_HIRES verified 96000 24 "$k: real content above 24 kHz"
done
"${FF[@]}" -i "$(file native_96000_dark_24)" -af "volume=-24dB" "${F24[@]}" "$(file native_96000_dark_quiet)"
emit native_96000_dark_quiet NATIVE_HIRES verified 96000 24 "dark, gain -24 dB"
"${FF[@]}" -i "$(file native_96000_dark_24)" -af "volume=14dB,alimiter=limit=0.9:level=disabled" "${F24[@]}" "$(file native_96000_dark_loud)"
emit native_96000_dark_loud NATIVE_HIRES verified 96000 24 "dark, gain +14 dB then limiter"
"${FF[@]}" -i "$(file native_44100_dark_24)" -af "volume=14dB,alimiter=limit=0.9:level=disabled" "${F24[@]}" "$(file native_44100_dark_loud)"
emit native_44100_dark_loud NATIVE_FULL na 44100 24 "dark, gain +14 dB then limiter"

# Genuine content with an analog floor at a stated level above 16-bit quantisation noise (floor stated at 96 kHz).
for d in 0 5 10 15 20; do
  python3 "$HERE/synth.py" "$OUT/base/bright_floor$d.wav" --kind bright --floor-db $d --floor-rate 96000
  "${FF[@]}" -i "$OUT/base/bright_floor$d.wav" -af "$(rs 96000 soxr)" "${F24[@]}" "$(file native_96000_bright_floor$d)"
  emit native_96000_bright_floor$d NATIVE_HIRES verified 96000 24 "analog floor +$d dB over 16-bit noise, real content above 24 kHz"
done

# Noise-shaped ultrasonic noise rising toward Nyquist, as in PCM made from DSD (synthetic emulation, not a DSD file).
"${FF[@]}" -i "$OUT/base/dsd.wav" "${F24[@]}" "$(file dsdlike_96000_24)"
emit dsdlike_96000_24 SYNTH_DSD na 96000 24 "dark body to 20 kHz plus rising shaped noise above 25 kHz"

# CD masters: 16-bit with TPDF dither.
for r in 44100 48000; do
  "${FF[@]}" -i "$OUT/base/dark.wav" -af "$(rs $r soxr dither)" "${F16[@]}" "$(file cd_${r}_dark_16)"
  emit cd_${r}_dark_16 NATIVE_FULL na $r 16 "CD master, TPDF dither"
done
"${FF[@]}" -i "$OUT/base/bright.wav" -af "$(rs 44100 soxr dither)" "${F16[@]}" "$(file cd_44100_bright_16)"
emit cd_44100_bright_16 NATIVE_FULL na 44100 16 "CD master, bright tilt, TPDF dither"
for v in quiet:-24dB loud:14dB,alimiter=limit=0.9:level=disabled; do
  "${FF[@]}" -i "$OUT/base/dark.wav" -af "volume=${v#*:},$(rs 44100 soxr dither)" "${F16[@]}" "$(file cd_44100_dark_16_${v%%:*})"
  emit cd_44100_dark_16_${v%%:*} NATIVE_FULL na 44100 16 "CD master, ${v%%:*} (the dither floor stays, the music moves)"
done

# Upsampled copies. Output: f24 = resampled in float and stored in 24 bits; d16 = 16-bit TPDF dither in a 16-bit file;
# d16p = the dithered 16-bit data stored in a 24-bit file.
up() { # id source rate resampler output hires
  local id="$1" src="$2" r="$3" rsm="$4" mode="$5" h="$6" bits=24
  case "$mode" in
    f24) "${FF[@]}" -i "$src" -af "$(rs $r $rsm)" "${F24[@]}" "$(file $id)" ;;
    d16) "${FF[@]}" -i "$src" -af "$(rs $r $rsm dither)" "${F16[@]}" "$(file $id)"; bits=16 ;;
    d16p) "${FF[@]}" -i "$src" -af "$(rs $r $rsm dither)" "${F16[@]}" "$OUT/base/$id.tmp.flac"
          "${FF[@]}" -i "$OUT/base/$id.tmp.flac" "${F24[@]}" "$(file $id)"; rm "$OUT/base/$id.tmp.flac" ;;
  esac
  emit "$id" UPSAMPLED "$h" "$r" "$bits" "from $(basename "$src" .flac), $rsm, $mode"
}
for rsm in soxr swr; do
  for r in 88200 96000 192000; do
    for mode in f24 d16 d16p; do up up_cd44_${r}_${rsm}_${mode} "$(file cd_44100_dark_16)" $r $rsm $mode not; done
  done
  for mode in f24 d16p; do up up_cd48_96000_${rsm}_${mode} "$(file cd_48000_dark_16)" 96000 $rsm $mode not; done
  up up_cd44_96000_bright_${rsm}_f24 "$(file cd_44100_bright_16)" 96000 $rsm f24 not
  up up_h96_192000_${rsm}_f24 "$(file native_96000_dark_24)" 192000 $rsm f24 verified
done

# The same from quiet and loud CD masters: the 16-bit floor does not move with the music.
for v in quiet loud; do
  up up_cd44_96000_soxr_d16_$v "$(file cd_44100_dark_16_$v)" 96000 soxr d16 not
  up up_cd44_96000_soxr_f24_$v "$(file cd_44100_dark_16_$v)" 96000 soxr f24 not
done

# 16-bit data in a 24-bit file, with and without a later gain change.
"${FF[@]}" -i "$(file native_44100_dark_24)" -af "aresample=dither_method=triangular" "${F16[@]}" "$OUT/base/p44.flac"
"${FF[@]}" -i "$OUT/base/p44.flac" "${F24[@]}" "$(file pad24_44100)"
emit pad24_44100 PADDED16 not 44100 24 "16-bit TPDF data zero-padded to 24"
"${FF[@]}" -i "$OUT/base/p44.flac" -af "volume=-3dB" "${F24[@]}" "$(file pad24_44100_gain)"
emit pad24_44100_gain PADDED16 not 44100 24 "padded, then gain -3 dB"
"${FF[@]}" -i "$(file native_96000_dark_24)" -af "aresample=dither_method=triangular" "${F16[@]}" "$OUT/base/p96.flac"
"${FF[@]}" -i "$OUT/base/p96.flac" "${F24[@]}" "$(file pad24_96000)"
emit pad24_96000 PADDED16 not 96000 24 "dark 96 kHz content, 16-bit TPDF data zero-padded to 24"
"${FF[@]}" -i "$OUT/base/p96.flac" -af "volume=-3dB" "${F24[@]}" "$(file pad24_96000_gain)"
emit pad24_96000_gain PADDED16 not 96000 24 "padded, then gain -3 dB"

# Lossy transcodes of the 44.1 kHz master, decoded back to FLAC.
SRC="$(file native_44100_dark_24)"
for k in 128 192 320; do
  "${FF[@]}" -i "$SRC" -c:a libmp3lame -b:a ${k}k "$OUT/base/mp3_$k.mp3"
  "${FF[@]}" -i "$OUT/base/mp3_$k.mp3" "${F16[@]}" "$(file lossy_mp3_${k})"
  emit lossy_mp3_${k} LOSSY_TRANSCODE na 44100 16 "LAME CBR $k kbps decoded to FLAC"
done
for k in 128 256; do
  "${FF[@]}" -i "$SRC" -c:a aac -b:a ${k}k "$OUT/base/aac_$k.m4a"
  "${FF[@]}" -i "$OUT/base/aac_$k.m4a" "${F16[@]}" "$(file lossy_aac_${k})"
  emit lossy_aac_${k} LOSSY_TRANSCODE na 44100 16 "FFmpeg AAC $k kbps decoded to FLAC"
done
echo "$(($(wc -l < "$MAN") - 1)) files in $OUT"
