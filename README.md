# syncthing-android

[![Build Status](https://travis-ci.org/syncthing/syncthing-android.svg?branch=master)](https://travis-ci.org/syncthing/syncthing-android)
[![License: MPLv2](https://img.shields.io/badge/License-MPLv2-blue.svg)](https://opensource.org/licenses/MPL-2.0)
[![Bountysource](https://api.bountysource.com/badge/tracker?tracker_id=1183310)](https://www.bountysource.com/teams/syncthing-android)

A wrapper of [Syncthing](https://github.com/syncthing/syncthing) for really old versions of Android (v9). Mainly for SONY pmca cameras.

# for building on SONY pmca

Make sure to set the max-folder concurrency to something low (like 2).

1) remove any references to android_log_printf, and dlopen/dlclose etc. in /usr/lib/go/src/runtime/cgo/gcc_android.c
   Otherwise a static binary can not be built. or apply patch < gcc_android_v9.patch

2) add something really hacky with cp syncthing/gobuild/standalone-ndk/android-10-arm/sysroot/usr/lib/libm.a syncthing/gobuild/standalone-ndk/android-10-arm/sysroot/usr/lib/liblog.a

# installing on SONY pmca

 If there is enough space on your internal memory (about 50mb) you should be able to install the .apk directly. If not you need to remove the app/src/main/assets/sthing.ext2.z file, transfer it to your sdcard manually and unpack it there to create the file /mnt/sdcard/sthing.ex2. This is a ext2 filesystem image with contains the syncthing binary and a default config file.

# syncthing-android

The [syncthing-wrapper](https://github.com/syncthing/syncthing-android) for really old Android versions, mainly for use with Sony cameras. 

# Building

### Dependencies
- Android SDK (you can skip this if you are using Android Studio)
- Android NDK (`$ANDROID_NDK_HOME` should point at the root directory of your NDK)
- Go (see [here](https://docs.syncthing.net/dev/building.html#prerequisites) for the required version)
- Java Version 8 (you might need to set `$JAVA_HOME` accordingly)

### Build instructions

Make sure you clone the project with
`git clone https://github.com/syncthing/syncthing-android.git --recursive`. Alternatively, run
`git submodule init && git submodule update` in the project folder.

Build Syncthing using `./gradlew buildNative`. Then use `./gradlew assembleDebug` or
Android Studio to build the apk.

# License

The project is licensed under the [MPLv2](LICENSE).
