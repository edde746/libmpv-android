# libmpv for Android

Based on [mpv-android](https://github.com/mpv-android/mpv-android)

## Building from source

Take a look at [README.md](buildscripts/README.md) inside the `buildscripts` directory.

## License

This project as a whole is licensed under the **GNU General Public License v3.0 or
later** (see [LICENSE](LICENSE)).

GPLv3+ is not a choice so much as a consequence of the build configuration: FFmpeg
is built with `--enable-gpl --enable-version3`, which FFmpeg's own `configure`
resolves to "GPL version 3 or later", and mpv is built with the default
`-Dgpl=true` (GPLv2 or later). The `libmpv.so` and `libav*.so` binaries shipped in
the AAR are therefore GPLv3+, and any application linking this library must be
GPLv3-compatible.

The patches under `buildscripts/patches/` modify files that are LGPLv2.1-or-later
upstream (mpv, FFmpeg, libplacebo). Our changes to them are distributed under
GPLv3-or-later, as permitted by LGPLv2.1 section 3. The unmodified upstream code
remains available from upstream under its original terms.

The Kotlin/JNI wrapper in `libmpv/src/` and the scripts in `buildscripts/` derive
from [mpv-android](https://github.com/mpv-android/mpv-android) and
[jarnedemeulemeester/libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android),
which are MIT licensed. That grant is retained in [LICENSE.MIT](LICENSE.MIT) and
continues to apply to the code inherited from those projects.
