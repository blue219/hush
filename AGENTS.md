## Description

**Hush ** is an Android meditation app powered by the Muse 2 brain-sensing headband.

## Goal

Build a polished meditation experience where users can visually perceive changes in their physiological state while meditating.
Core goals:

- Connect reliably to a Muse 2 over Bluetooth.
- Receive and process EEG, PPG, and IMU sensor data in real time.
- Extract useful features such as Alpha, Theta, Beta, heart rate, and stillness.
- Smooth and normalize sensor data before it affects the UI.
- Provide session timing, history, and post-session summaries.
- Generate a unique visual representation ("Mindprint") for completed sessions.
- Maintain smooth real-time rendering, ideally at 60 FPS.
- Keep signal processing, Muse integration, application state, and rendering logic clearly separated.
  
  
  

The visual system should respond gradually rather than directly mirroring noisy sensor values. The experience should feel organic, calm, and alive.

## Startup

## Tech

- Kotlin
- Jetpack Compose
- Muse SDK 8.0.9
- OpenGL ES / shaders

## Structure

## Rules
- Check files under "docs/" before coding
- only Use English on UI
- Use current exist UI style, do not change UI style without permissions.


