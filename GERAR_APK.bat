@echo off
setlocal
cd /d "%~dp0"
where gradle >nul 2>&1
if errorlevel 1 (
  echo Gradle nao encontrado.
  echo Use o GitHub Actions para gerar o APK automaticamente.
  pause
  exit /b 1
)
echo Gerando APK...
gradle assembleDebug --no-daemon
if errorlevel 1 (
  echo Falha ao gerar o APK.
  pause
  exit /b 1
)
echo APK gerado em:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
start "" "%CD%\app\build\outputs\apk\debug"
pause
