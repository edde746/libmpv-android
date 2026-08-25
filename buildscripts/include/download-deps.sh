#!/bin/bash -e

. ./include/depinfo.sh

[ -z "$WGET" ] && WGET=wget

mkdir -p deps && cd deps

# mbedtls
[ ! -d mbedtls ] && git clone --depth 1 --branch v$v_mbedtls --recurse-submodules https://github.com/Mbed-TLS/mbedtls.git mbedtls

# dav1d
[ ! -d dav1d ] && git clone --depth 1 --branch $v_dav1d https://code.videolan.org/videolan/dav1d.git dav1d

# libdovi (prebuilt static libs from edde746/libdovi-builds - the same
# artifacts the Plezy Android ExoPlayer DV module consumes; the C API header
# is vendored at include/libdovi/rpu_parser.h)
if [ ! -d libdovi ]; then
	mkdir libdovi
	for triple in aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android; do
		mkdir -p libdovi/$triple
		$WGET https://github.com/edde746/libdovi-builds/releases/download/v$v_libdovi/libdovi-$triple.tar.gz -O - | \
			tar -xz -C libdovi/$triple
	done
fi

# ffmpeg
[ ! -d ffmpeg ] && git clone --depth 1 --branch n$v_ffmpeg https://github.com/FFmpeg/FFmpeg.git ffmpeg

# freetype2
[ ! -d freetype ] && git clone --depth 1 --branch VER-${v_freetype//./-} https://gitlab.freedesktop.org/freetype/freetype.git freetype

# fribidi
[ ! -d fribidi ] && git clone --depth 1 --branch v$v_fribidi https://github.com/fribidi/fribidi.git fribidi

# harfbuzz
[ ! -d harfbuzz ] && git clone --depth 1 --branch $v_harfbuzz https://github.com/harfbuzz/harfbuzz.git harfbuzz

# libunibreak
if [ ! -d libunibreak ]; then
	mkdir libunibreak
	$WGET https://github.com/adah1972/libunibreak/releases/download/libunibreak_${v_libunibreak//./_}/libunibreak-${v_libunibreak}.tar.gz -O - | \
		tar -xz -C libunibreak --strip-components=1
fi

# libass (edde746 fork: upstream 0.17.4 + render-path optimizations, versioned 0.18.x;
# same fork the Plezy Android ExoPlayer subtitle module consumes as prebuilts)
[ ! -d libass ] && git clone --depth 1 --branch $v_libass https://github.com/edde746/libass.git libass

# lua
if [ ! -d lua ]; then
	mkdir lua
	$WGET http://www.lua.org/ftp/lua-$v_lua.tar.gz -O - | \
		tar -xz -C lua --strip-components=1
fi

[ ! -d libplacebo ] && git clone --depth 1 --branch v$v_libplacebo --recurse-submodules https://code.videolan.org/videolan/libplacebo.git libplacebo

# mpv
[ ! -d mpv ] && git clone --depth 1 --branch v$v_mpv https://github.com/mpv-player/mpv.git mpv

cd ..
