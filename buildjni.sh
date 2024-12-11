#!/bin/bash

git clone --recursive https://github.com/eycorsican/leaf
cd leaf
git checkout v0.11.0

git apply --check ../leaf.patch
git apply ../leaf.patch

sh ./scripts/build_android.sh

cp ./target/leaf-android-libs/libleaf-aarch64-linux-android.so ../app/src/main/jniLibs/arm64-v8a/libleaf.so
cp ./target/leaf-android-libs/libleaf-armv7-linux-androideabi.so ../app/src/main/jniLibs/armeabi-v7a/libleaf.so
cp ./target/leaf-android-libs/libleaf-i686-linux-android.so ../app/src/main/jniLibs/x86/libleaf.so
cp ./target/leaf-android-libs/libleaf-x86_64-linux-android.so ../app/src/main/jniLibs/x86_64/libleaf.so

cd ..

rm -rf leaf

git clone --recursive https://github.com/Gowee/noisy-shuttle
cd noisy-shuttle

git checkout v0.2.3

cp ../rustls-patched-fix.patch ./rustls-patched
cd rustls-patched
git apply --check rustls-patched-fix.patch
git apply rustls-patched-fix.patch
cd ..

export CC_aarch64_linux_android="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android21-clang"
export AR_aarch64_linux_android="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"

cargo build --target aarch64-linux-android --release

export CC_armv7_linux_androideabi="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/armv7a-linux-androideabi21-clang"
export AR_armv7_linux_androideabi="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"

cargo build --target armv7-linux-androideabi --release

export CC_i686_linux_android="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/i686-linux-android21-clang"
export AR_i686_linux_android="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"

cargo build --target i686-linux-android --release

export CC_x86_64_linux_android="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/x86_64-linux-android21-clang"
export AR_x86_64_linux_android="$NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ar"

cargo build --target x86_64-linux-android --release

cp ./target/aarch64-linux-android/release/noisy-shuttle ../app/src/main/jniLibs/arm64-v8a/libns.so
cp ./target/armv7-linux-androideabi/release/noisy-shuttle ../app/src/main/jniLibs/armeabi-v7a/libns.so
cp ./target/i686-linux-android/release/noisy-shuttle ../app/src/main/jniLibs/x86/libns.so
cp ./target/x86_64-linux-android/release/noisy-shuttle ../app/src/main/jniLibs/x86_64/libns.so

cd ..

rm -rf noisy-shuttle