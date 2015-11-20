#/bin/sh
./getPlugins.sh
set -e
: ${IDEA_HOME?"Need to set IDEA_HOME to point to a valid IntelliJ IDEA installation."}

## Build the BugVM IDEA plugin
cd bugvm/bugvm-idea/idea
awk '!/idea-version/' src/main/resources/META-INF/plugin.xml > plugin.xml.tmp && mv plugin.xml.tmp src/main/resources/META-INF/plugin.xml
cd ..
mvn -Didea.home="$IDEA_HOME" clean package -U
git checkout -- idea/src/main/resources/META-INF/plugin.xml
cd ../..

## Apply versioning to IdeaApplicationInfo.xml based on
## version found in bugvm-idea/pom.xml. The version
## is defined by the BUGVM_IDEA_PLUGIN_VERSION environment
## variable, based on which the respective tag will be
## pulled in
javac -d . bugvm/bugvm-studio-branding/src/Versioning.java
version=$(java -cp . Versioning bugvm/bugvm-idea/idea/pom.xml bugvm/bugvm-studio-branding/src/idea/IdeaApplicationInfo.xml bugvm/bugvm-studio-dmg/dmg.json)
rm Versioning.class

## Build IntelliJ IDEA using our own build files
ant -f build-bugvm.xml
rm -rf out/bugvm-studio
mkdir -p out/bugvm-studio

## Copy the artifacts and build the DMG
cp out/artifacts/*.mac.zip out/bugvm-studio/bugvm-studio-$version.zip
cd out/bugvm-studio
unzip bugvm-studio-$version.zip
cd ../..
appdmg bugvm/bugvm-studio-dmg/dmg.json out/bugvm-studio/bugvm-studio-$version.dmg