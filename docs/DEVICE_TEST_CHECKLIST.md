# Manual Galaxy A36 5G test checklist (nothing here has been run yet)

Record: date, build version, Android/One UI version, lighting, subject, result, notes.

## Install and launch
- [ ] Debug APK installs; app launches; no INTERNET permission in `adb shell dumpsys package com.eljihani.veinvision`
- [ ] OpenCV loads (otherwise the red error screen appears)
- [ ] Camera permission prompt: deny -> explanatory screen; grant -> preview

## Camera
- [ ] Live preview fills the 3:4 box without distortion
- [ ] Zoom slider works and matches the preview; exposure slider hidden/disabled if unsupported
- [ ] White-balance lock shown only if supported; lock visibly stops colour drift
- [ ] Front/back toggle appears only if both exist; switching works
- [ ] Capture still -> file in SESSIONS, opens correctly, orientation correct
- [ ] Put app in background and return: preview resumes
- [ ] Open another camera app while this runs: error message appears, no crash
- [ ] Rotate phone: UI stays portrait

## Quality
- [ ] Cover lens -> POOR "Too dark", overlay suppressed, original shown, warning visible
- [ ] Point at bright lamp -> POOR/MARGINAL "Too bright"/overexposed
- [ ] Shake phone -> motion indicator rises; defocus -> sharpness indicator falls
- [ ] Point at skin vs non-skin: skin-coloured area changes; near-black frame shows "n/a (invalid)"
- [ ] Calibrate thresholds in SETTINGS and note chosen values

## Pipeline
- [ ] Each view (Enhanced, Vesselness, Mask, Overlay) aligns with the preview; Original returns instantly with no camera restart
- [ ] Each stage toggle and slider visibly changes the output
- [ ] Labels never say "vein detected"/"confirmed"

## Performance (SETTINGS -> Generate report)
- [ ] Hold a steady scene in each processed view for >= 60 s; copy report; record at several processing widths

## Hardware/Models/Privacy
- [ ] HARDWARE lists real cameras; NIR shows NOT CONNECTED; plugging a USB device lists identity only, capability UNVERIFIED
- [ ] MODELS shows NOT INSTALLED
- [ ] Delete capture / delete all works
