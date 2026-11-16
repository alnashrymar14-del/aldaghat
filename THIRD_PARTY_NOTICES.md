# Third-Party Notices — الضغاط / Al-Daghat

This application is distributed under the MIT license (see `LICENSE`). It builds on the following
third-party components. All of them are Apache-2.0 except the test-only libraries noted below; **no
GPL or LGPL component is bundled**.

## Runtime dependencies

| Component | Version | License | Use |
| --- | --- | --- | --- |
| AndroidX Media3 Transformer | 1.8.0 | Apache-2.0 | Transcoding pipeline (`Transformer`, encoder settings, effects) |
| AndroidX Media3 ExoPlayer | 1.8.0 | Apache-2.0 | In-app video preview |
| AndroidX Media3 UI | 1.8.0 | Apache-2.0 | `PlayerView` surface |
| AndroidX Media3 Effect | 1.8.0 | Apache-2.0 | `Presentation` scaling effect |
| AndroidX Media3 Muxer | 1.8.0 | Apache-2.0 | MP4 muxing used by Transformer |
| AndroidX Media3 Common | 1.8.0 | Apache-2.0 | Shared media types |
| AndroidX Core / Activity / Lifecycle | 1.16.0 / 1.10.1 / 2.9.1 | Apache-2.0 | Platform integration, `ServiceCompat`, ViewModel |
| Jetpack Compose (UI, Foundation, Material 3, Runtime) | Compose BOM 2025.06.01 | Apache-2.0 | Entire UI |
| Kotlin Standard Library | 2.2.20 | Apache-2.0 | Language runtime |
| kotlinx.coroutines | 1.10.2 | Apache-2.0 | Concurrency |
| Guava (transitive, Android artifact) | via Media3 | Apache-2.0 | Media3 internals |

Full license texts: <https://www.apache.org/licenses/LICENSE-2.0> and
<https://developer.android.com/jetpack/androidx/releases>.

## Build- and test-only dependencies

| Component | Version | License |
| --- | --- | --- |
| JUnit 4 | 4.13.2 | Eclipse Public License 1.0 |
| AndroidX Test (runner, rules, core, ext:junit) | 1.6.x / 1.2.1 | Apache-2.0 |
| kotlinx-coroutines-test | 1.10.2 | Apache-2.0 |

These are not shipped inside the APK.

## Explicitly not included

- **FFmpeg / FFmpegKit / FFmpegKitNext** — not linked, not bundled, no native media libraries. There
  is therefore no LGPL/GPL obligation from FFmpeg and no need for a source-offer for native libraries.
  All transcoding uses the device's own codecs through Media3 Transformer.
- **No GPL-licensed code** of any kind.
- **No network, analytics, crash-reporting or ads SDKs.**

## Native code and 16 KB page size

The only native libraries inside the APK come from AndroidX transitively (`libandroidx.graphics.path.so`,
shipped as 16 KB-compatible by AndroidX at the versions above, for `arm64-v8a`, `armeabi-v7a`,
`x86_64` and `x86`). No custom native code is compiled, so there are no hand-built `.so` files that
could break 16 KB page alignment. `armeabi` (the deprecated ABI) is not used.

## Codec patent considerations

H.264/AVC and H.265/HEVC are covered by patents held by various parties, and their use may require a
license for commercial distribution depending on jurisdiction and business model.

- Al-Daghat performs encoding **exclusively through the platform MediaCodec encoders** that are
  already licensed and shipped with the user's device. The application does not bundle its own
  encoder implementation (unlike an FFmpeg build with x264/x265, which would carry additional
  patent and license obligations).
- Even so, if you publish this app commercially, review the H.264/HEVC patent licensing position
  (e.g. Access Advance / Via LA HEVC pools and MPEG LA AVC terms) for your distribution model —
  especially for HEVC, which is enabled only on devices that expose an HEVC encoder.
- No codec software is downloaded at runtime; everything runs offline on the device.
