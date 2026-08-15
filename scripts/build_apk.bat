@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=C:\Users\Alish\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%
set GRADLE_BIN=C:\Users\Alish\.gradle\wrapper\dists\gradle-8.13-bin\5xuhj0ry160q40clulazy9h7d\gradle-8.13\bin\gradle.bat

set PROJECT_DIR=%~dp0..
cd /d "%PROJECT_DIR%\android"
echo [BUILD] Generating wrapper...
call "%GRADLE_BIN%" wrapper --gradle-version 8.7

echo [BUILD] Building Android APK (assembleDebug)...
call "%GRADLE_BIN%" assembleDebug --stacktrace
