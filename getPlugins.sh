#!/usr/bin/env bash
rm -rf android
git clone git://git.jetbrains.org/idea/android.git android
cd android
git pull
git checkout idea/143.1511
cd ..
git clone git://git.jetbrains.org/idea/adt-tools-base.git android/tools-base
cd android/tools-base
git pull
git checkout idea/143.1511
cd ../..
