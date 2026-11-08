#!/usr/bin/env bash
# Builds FFmpegKitNext for Android from the checkout in $KIT_DIR and stages a Maven bundle in $OUT_DIR.
# Inputs: KIT_DIR, OUT_DIR, ARCHS (comma list for --arch), PROFILE (full|minimal), REVISION, GROUP_ID.
set -euo pipefail

: "${KIT_DIR:?}" "${OUT_DIR:?}" "${ARCHS:?}" "${PROFILE:?}" "${REVISION:?}" "${GROUP_ID:?}"

# Decoders, demuxers, filters and the PCM/WAV/null outputs the analyser and FFprobe need; nothing else.
MINIMAL_FLAGS="--disable-everything \
--enable-protocol=file,pipe \
--enable-demuxer=flac,mov,mp3,ogg,wav,aac,ape,dsf,iff,aiff,caf,w64,wv,tta,mpc,mpc8,matroska,asf,amr,ac3,eac3,dts,dtshd,truehd,mlp,tak,xwma,oma,au,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le \
--enable-decoder=flac,alac,mp3,mp3float,aac,aac_latm,opus,vorbis,wavpack,ape,tta,tak,wmav1,wmav2,wmalossless,wmapro,ac3,eac3,dca,truehd,mlp,mpc7,mpc8,dsd_lsbf,dsd_msbf,dsd_lsbf_planar,dsd_msbf_planar,amrnb,amrwb,pcm_s16le,pcm_s16be,pcm_s24le,pcm_s24be,pcm_s32le,pcm_s32be,pcm_f32le,pcm_f32be,pcm_f64le,pcm_u8,pcm_alaw,pcm_mulaw \
--enable-parser=flac,mpegaudio,aac,aac_latm,opus,vorbis,ac3,dca,mlp,tak,cook \
--enable-encoder=pcm_f32le,pcm_s16le \
--enable-muxer=f32le,s16le,wav,null \
--enable-filter=aresample,aformat,anull,abuffer,abuffersink,atrim,volume,buffer,buffersink,null,format \
--enable-bsf=aac_adtstoasc,null"

cd "$KIT_DIR"

if [ "$PROFILE" = minimal ]; then
  grep -q '^  --disable-programs \\$' scripts/android/ffmpeg.sh
  sed -i "s|^  --disable-programs \\\\\$|&\n  ${MINIMAL_FLAGS} \\\\|" scripts/android/ffmpeg.sh
  grep -q -- '--disable-everything' scripts/android/ffmpeg.sh
fi

./android.sh --arch="$ARCHS" --api-level=24

bundle=$(find prebuilt -maxdepth 1 -type d -name 'bundle-android-aar-*-maven' | head -n 1)
dir=$(find "$bundle/com/arthenica/ffmpeg-kit-next" -mindepth 1 -maxdepth 1 -type d | head -n 1)
upstream=$(basename "$dir")
version="${upstream}-${REVISION}"

dest="$OUT_DIR/${GROUP_ID//.//}/ffmpeg-kit-next/$version"
mkdir -p "$dest"
cp "$dir/ffmpeg-kit-next-$upstream.aar" "$dest/ffmpeg-kit-next-$version.aar"

# Same POM, re-homed under our group and version so it never collides with the retired upstream coordinates.
python3 - "$dir/ffmpeg-kit-next-$upstream.pom" "$dest/ffmpeg-kit-next-$version.pom" "$GROUP_ID" "$upstream" "$version" <<'PY'
import sys
src, out, group, old, new = sys.argv[1:]
text = open(src).read()
text = text.replace("<groupId>com.arthenica</groupId>", f"<groupId>{group}</groupId>", 1)
text = text.replace(f"<version>{old}</version>", f"<version>{new}</version>", 1)
open(out, "w").write(text)
PY

{
  echo "version=$version"
  echo "dest=$dest"
} >> "${GITHUB_OUTPUT:-/dev/null}"
ls -l "$dest"
