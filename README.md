# SubGHz Signal Generator

I made this because I'm pretty sure nothing like it exists for android and it goes hand in hand with doing things on the fly. It's convenient, fast and pretty user friendly. There may be bugs but overall it's functional as is.  That being said, it's open source... fork it, improve it, enjoy it! If it helped you, great, I expect nothing in return except proper credit if necessary. I've also included a pre-built app in app/build/*


An Android app for generating Flipper Zero `.sub` files directly on your phone. Create, convert, inspect, and audibly preview sub-GHz signals without needing a PC.

## Features

- **Generator** - Build `.sub` files from scratch using protocol encoding, binary, hex, raw timing input, or a DTMF keypad
- **Converter** - Load existing signal files and convert them to Flipper `.sub` format
- **Inspector** - Analyze `.sub` files with waveform visualization, timing statistics, and audio playback

### Supported Protocols

Princeton, CAME, Nice FLO, Linear, GateTX, Holtek, Manchester, PWM, PPM, NRZ, DTMF

### Supported Frequencies

All standard Flipper Zero sub-GHz frequencies (300 MHz - 925 MHz) plus custom frequency input.

## Output

Generated files are saved to `/sdcard/sub-files/` and are ready to copy to your Flipper Zero's SD card under `subghz/`.
	*** Just a foot note, the files will be overwritten if you dont name them, no extension necessary.

## Usage Tips

**Signal files are tiny.** A typical `.sub` file is a few kilobytes. If you're trying to load a multi-megabyte file, it's not a sub-GHz signal — don't do it. The app caps binary input at 512 KB and timing arrays at 50,000 entries to keep things sane.

**Audio playback is a preview, not a radio.** The audio engine converts pulse timings to audible tones so you can hear the signal pattern. It does not transmit anything in the sub-ghz frequencies, but DTMF obviously can be used. Playback is capped at 30 seconds and 20,000 timings

**Waveform rendering has limits.** The canvas downsamples signals beyond 4,000 timings for display. Your full signal is still exported — the waveform view is just a visual summary.

**RAW_Data format matters.** Flipper `.sub` files use alternating positive (HIGH) and negative (LOW) microsecond values. Positive = mark, negative = space. If your manually entered timings look wrong on the Flipper, double-check your signs.

**DTMF sequences are capped at 32 digits.** Each digit generates a dual-tone waveform that gets converted to OOK pulse timings. Longer sequences would blow past timing limits.

**Protocol encoding is fixed-parameter.** The built-in protocol encoders (Princeton, CAME, etc.) use standard timing values for each protocol. If you need custom pulse widths, use Raw input mode instead.

## Requirements

- Android 7.0+ (API 24)
- Storage permission for saving `.sub` files

## Building

Built with Jetpack Compose and Kotlin. Standard Android Gradle build:

```
./gradlew assembleDebug
```
Built on my Samsung S22 using a fork of the old AndroidIDE app, now called Android Code Studio. 
https://github.com/AndroidCSOfficial/android-code-studio

## Warning
I suppose I need to include this to cover my ass. It is NOT my responsibility for what YOU choose to do with the capabilities made available in this app. I made it as a means for security testing and possible scientific research to make it easier for anyone just starting out or for professionals, not so you can try to break into your neighbors' shitty tesla truck. Thank you.

## License

MIT
