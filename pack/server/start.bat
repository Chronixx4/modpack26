@echo off
rem Startet den Server. Beim ersten Start wird NeoForge automatisch installiert.
cd /d "%~dp0"
set NEOFORGE=@NEOFORGE@
if not exist "libraries\net\neoforged\neoforge\%NEOFORGE%\win_args.txt" (
  echo Installiere NeoForge %NEOFORGE% ...
  java -jar "neoforge-%NEOFORGE%-installer.jar" --installServer
  if errorlevel 1 (
    echo NeoForge-Installation fehlgeschlagen
    pause
    exit /b 1
  )
  del /q run.bat run.sh 2>nul
)
java @user_jvm_args.txt @libraries/net/neoforged/neoforge/%NEOFORGE%/win_args.txt nogui %*
pause
