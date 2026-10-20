@echo off
chcp 65001 >nul
title Clean einrichten
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
set APK=%~dp0Clean.apk
set PKG=de.cleanphone.launcher

echo.
echo  ==============================================
echo     Clean auf dein Samsung-Handy bringen
echo  ==============================================
echo.
if not exist "%APK%" (
  echo  Lade Clean herunter ...
  curl -L -s -o "%APK%" https://github.com/nikoatonchev-debug/clean-phone/releases/latest/download/Clean.apk
)
if not exist "%APK%" (
  echo  Clean konnte nicht geladen werden. Internet pruefen.
  goto ende
)

rem USB-Werkzeug von Google holen, falls es auf diesem PC noch fehlt.
if not exist "%ADB%" (
  where adb >nul 2>&1 && (for /f "delims=" %%a in ('where adb') do set ADB=%%a)
)
if not exist "%ADB%" (
  echo  Lade USB-Werkzeug von Google herunter ...
  curl -L -s -o "%TEMP%\platform-tools.zip" https://dl.google.com/android/repository/platform-tools-latest-windows.zip
  tar -xf "%TEMP%\platform-tools.zip" -C "%TEMP%"
  set ADB=%TEMP%\platform-tools\adb.exe
)

echo  1. Handy mit dem USB-Kabel an den PC anschliessen.
echo  2. Auf dem Handy "USB-Debugging zulassen" mit "Zulassen" bestaetigen.
echo.
echo  Warte auf das Handy ...
"%ADB%" wait-for-device
"%ADB%" devices | find "unauthorized" >nul && (
  echo  Bitte auf dem Handy "Zulassen" antippen und diese Datei neu starten.
  goto ende
)
echo  Handy gefunden.
echo.

echo  Clean wird installiert ...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo  Die Installation hat nicht geklappt.
  goto ende
)

rem Erlaubt den Schwarz-Weiss-Schalter in den Einstellungen.
"%ADB%" shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS >nul 2>&1

rem Clean als Startbildschirm festlegen.
"%ADB%" shell cmd package set-home-activity %PKG%/.HomeActivity >nul 2>&1

echo.
echo  ----------------------------------------------
echo   Volle Sperre der anderen Apps einrichten?
echo  ----------------------------------------------
echo   Dafuer muessen auf dem Handy vorher ALLE Konten
echo   abgemeldet sein: Einstellungen - Konten und Sicherung
echo   - Konten verwalten - jedes Konto entfernen.
echo   (Google, Samsung, WhatsApp-Konto usw. Danach kannst
echo   du dich wieder anmelden.)
echo.
set /p VOLL= Jetzt einrichten? (j/n):
if /i not "%VOLL%"=="j" goto start

"%ADB%" shell dpm set-device-owner %PKG%/.AdminReceiver > "%TEMP%\clean_dpm.txt" 2>&1
find "Success" "%TEMP%\clean_dpm.txt" >nul
if errorlevel 1 (
  echo.
  echo  Die volle Sperre hat nicht geklappt. Meistens ist noch ein Konto
  echo  angemeldet oder der "Sichere Ordner" ist eingerichtet.
  echo  Meldung vom Handy:
  type "%TEMP%\clean_dpm.txt"
  echo.
  echo  Clean funktioniert trotzdem, nur ohne volle Sperre.
) else (
  echo  Volle Sperre ist aktiv. Melde dich jetzt wieder bei deinen Konten an.
)

:start
"%ADB%" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >nul 2>&1
echo.
echo  Fertig! Schau auf dein Handy.

:ende
echo.
pause
