@echo off
rem Launch a Minecraft dev client with a Krios mod loaded.
rem Usage: run-client.bat [neoforge^|fabric] [mod]   (defaults: neoforge, industry)
setlocal

set "LOADER=%~1"
if "%LOADER%"=="" set "LOADER=neoforge"
set "MOD=%~2"
if "%MOD%"=="" set "MOD=industry"

if not "%LOADER%"=="neoforge" if not "%LOADER%"=="fabric" (
    echo Usage: run-client.bat [neoforge^|fabric] [mod]
    exit /b 1
)

set "DIR=%~dp0..\mods\%MOD%"
if not exist "%DIR%\gradlew.bat" (
    echo Unknown mod "%MOD%": no gradlew.bat in %DIR%
    exit /b 1
)

pushd "%DIR%"
call gradlew.bat %LOADER%:runClient
set "EXIT=%ERRORLEVEL%"
popd
exit /b %EXIT%
