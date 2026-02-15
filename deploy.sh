#!/bin/bash

# Configuration
TV_IP="192.168.1.105:5555"
PACKAGE_NAME="com.saihgupr.btcontrol"
ACTIVITY_NAME=".MainActivity"
APK_PATH="app/build/outputs/apk/debug/AndroidTVBluetooth.apk"

echo "--- Building Debug APK ---"
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo "--- Checking ADB Connection ---"
adb connect $TV_IP
adb devices | grep -q "$TV_IP.*device"
if [ $? -ne 0 ]; then
    echo "TV not found or not authorized at $TV_IP"
    exit 1
fi

echo "--- Installing APK ---"
# Using -r to replace, but first trying to install directly
adb -s $TV_IP install -r $APK_PATH
if [ $? -ne 0 ]; then
    echo "Install failed, attempting uninstall/reinstall..."
    adb -s $TV_IP uninstall $PACKAGE_NAME
    adb -s $TV_IP install $APK_PATH
fi

echo "--- Launching App ---"
adb -s $TV_IP shell am start -n $PACKAGE_NAME/$ACTIVITY_NAME

echo "--- Deployment Complete ---"
