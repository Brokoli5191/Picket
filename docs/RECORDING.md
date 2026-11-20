# Recording the demo GIF

The README has a slot for a short screen recording (`docs/assets/demo.gif`, under 5 MB so GitHub
shows it inline). It is recorded on a real phone with Shizuku running, because the emulator has no
Shizuku and would only show the connection screen.

## 1. Clean status bar (optional)

SystemUI's demo mode freezes the clock, battery and signal icons. One UI honours most of it; check
on your phone and skip what it ignores.

```sh
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# afterwards:
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

## 2. Record

At most 30 seconds: Processes (scroll, open an app), Watchers, an action with its command preview,
undo from the snackbar.

```sh
adb shell screenrecord --bit-rate 8M --time-limit 30 /sdcard/picket-demo.mp4
adb pull /sdcard/picket-demo.mp4
adb shell rm /sdcard/picket-demo.mp4
```

`screenrecord` stops at the time limit or on Ctrl+C. It does not record audio, and some screens
(banking apps, DRM video) come out black, which does not matter here.

## 3. Convert to GIF

Two passes with a generated palette keep the file small and the colours clean:

```sh
ffmpeg -i picket-demo.mp4 -vf "fps=15,scale=360:-1:flags=lanczos,palettegen=stats_mode=diff" palette.png
ffmpeg -i picket-demo.mp4 -i palette.png \
  -lavfi "fps=15,scale=360:-1:flags=lanczos [x]; [x][1:v] paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
  docs/assets/demo.gif
du -h docs/assets/demo.gif
```

Over 5 MB: lower `fps` to 12, the width to 320, or cut the recording shorter
(`-ss 2 -t 20` before `-i`).

## 4. Check before committing

- No real notifications, contacts, account names or other people's apps you would not show publicly.
- The journal entries visible in the recording belong to the test target or harmless apps.
- Then replace the placeholder comment in the README's screenshots section with
  `![Picket in use](docs/assets/demo.gif)`.
