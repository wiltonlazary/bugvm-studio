#!/bin/bash

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

version=$BUGVM_IDEA_PLUGIN_VERSION
rm -rf bugvm/bugvm-idea
mkdir bugvm/bugvm-idea
cd bugvm/bugvm-idea
wget https://github.com/bugvm/bugvm-idea/releases/download/bugvm-idea-$version/bugvm-idea-$version-plugin-dist.jar
cd ../..
