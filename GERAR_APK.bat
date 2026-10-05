@echo off
setlocal
cd /d "%~dp0"

if not exist gradlew.bat (
  echo Gradle Wrapper nao encontrado.
  echo Abra o projeto no Android Studio ou use o GitHub Actions.
  pause
  exit /b 1
)

echo Gerando APK com o Gradle Wrapper...
call gradlew.bat --no-daemon clean assembleDebug
if errorlevel 1 (
  echo.
  echo Falha ao gerar o APK.
  pause
  exit /b 1
)

echo.
echo APK gerado com sucesso:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
start "" "%CD%\app\build\outputs\apk\debug"
pause
