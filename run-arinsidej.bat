@echo off
setlocal enabledelayedexpansion

rem ARInsideJ launcher. Run from anywhere - always uses this script's own folder as the working
rem directory, so arinsidej.jar/settings.ini next to it are found regardless of where you call it
rem from. Any arguments you pass are forwarded as-is (e.g. run-arinsidej.bat -s myserver -l Demo
rem -p mypass); with no arguments it falls back to -i settings.ini in this same folder.
rem
rem The BMC AR System Java API jars (arapi*.jar, arlogger*.jar) are NOT bundled. Drop them into
rem the lib\ folder next to this script; every *.jar in there is added to the classpath. See
rem lib\README.txt for what is needed and where to get it.

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%arinsidej.jar"
set "LIB_DIR=%SCRIPT_DIR%lib"

if not exist "%JAR%" (
    echo [ERR] %JAR% not found. Put it next to this script - download a release build from
    echo       https://github.com/ljlongwing/ARInsideJ/releases/latest or build it with: mvn -o package
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo [ERR] java not found on PATH. Install a JDK/JRE 17+ and try again.
    exit /b 1
)

rem Build the classpath: the app jar plus every jar in lib\.
set "CP=%JAR%"
set "ARAPI_FOUND="
if exist "%LIB_DIR%\*.jar" (
    for %%J in ("%LIB_DIR%\*.jar") do (
        set "CP=!CP!;%%~fJ"
        echo %%~nxJ | findstr /b /i "arapi" >nul && set "ARAPI_FOUND=1"
    )
)

if not defined ARAPI_FOUND (
    echo [ERR] No arapi*.jar found in %LIB_DIR%
    echo       ARInsideJ needs BMC's AR System Java API jars ^(arapi*.jar, arlogger*.jar^).
    echo       They are proprietary and not bundled. Copy them into:
    echo           %LIB_DIR%
    echo       See %LIB_DIR%\README.txt for every place they can be found.
    exit /b 1
)

pushd "%SCRIPT_DIR%"
if "%~1"=="" (
    java -cp "%CP%" arinside.Launch -i settings.ini
) else (
    java -cp "%CP%" arinside.Launch %*
)
set "EXITCODE=%ERRORLEVEL%"
popd

exit /b %EXITCODE%
