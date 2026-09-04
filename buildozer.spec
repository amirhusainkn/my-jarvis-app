[app]

# (str) Title of your application
title = Jarvis

# (str) Package name
package.name = jarvis

# (str) Package domain (needed for android packaging)
package.domain = org.test

# (str) Source files where the let's go (relative to directory of buildozer.spec)
source.dir = .

# (list) Source files to include (let's keep empty to include all)
source.include_exts = py,png,jpg,kv,atlas

# (str) Application versioning
version = 0.1

# (list) Application requirements
# Yahan pyjnius aur zaroori libraries hain jo Android par kaam karengi
requirements = python3,kivy,plyer,pyjnius

# (str) Supported orientations
orientation = portrait

# (list) List of service to declare
#services = 

#
# Android specific
#

# (bool) Indicate if the application should be fullscreen or not
fullscreen = 0

# (string) Presets for android
android.api = 33
android.minapi = 21
android.ndk = 25b
android.accept_sdk_license = True

# (str) Android arch to build for, 
android.archs = arm64-v8a

# (list) Permissions
# Yeh sari zaroori permissions hain jo anti-theft, mic, aur camera ke liye chahiye
android.permissions = INTERNET,RECORD_AUDIO,FOREGROUND_SERVICE,WAKE_LOCK,READ_CONTACTS,CALL_PHONE,CAMERA,FLASHLIGHT

[buildozer]

# (int) Log level (0 = error only, 1 = info, 2 = debug (with command output))
log_level = 2

# (int) Display warning if buildozer is run as root (0 = False, 1 = True)
warn_on_root = 0
