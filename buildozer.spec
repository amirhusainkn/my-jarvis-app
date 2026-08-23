[app]
title = Jarvis
package.name = jarvis
package.domain = org.test
source.dir = .
source.include_exts = py,png,jpg,kv,atlas
version = 0.1
requirements = python3,kivy,plyer,SpeechRecognition,pyjnius

orientation = portrait
osx.python_version = 3
osx.kivy_version = 1.9.1
fullscreen = 0

android.api = 33
android.minapi = 21
android.ndk = 25b
android.accept_sdk_license = True
android.archs = arm64-v8a
android.permissions = INTERNET,RECORD_AUDIO,FOREGROUND_SERVICE,WAKE_LOCK,READ_CONTACTS,CALL_PHONE,CAMERA,FLASHLIGHT

[buildozer]
log_level = 2
warn_on_root = 0
