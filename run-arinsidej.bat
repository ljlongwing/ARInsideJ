@echo off
setlocal

rem ARInsideJ launcher. Run from anywhere - always uses this script's own folder as the working
rem directory, so arinsidej.jar/settings.ini next to it are found regardless of where you call it
rem from. Any arguments you pass are forwarded as-is (e.g. run-arinsidej.bat -s myserver -l Demo
rem -p mypass); with no arguments it falls back to -i settings.ini in this same folder.

set "SCRIPT_DIR=%~dp0"
set "JAR=%SCRIPT_DIR%arinsidej.jar"

if not exist "%JAR%" (
    echo [ERR] %JAR% not found. Build it first with: mvn -o package
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo [ERR] java not found on PATH. Install a JDK/JRE 17+ and try again.
    exit /b 1
)

pushd "%SCRIPT_DIR%"
if "%~1"=="" (
    java -jar "%JAR%" -i settings.ini
) else (
    java -jar "%JAR%" %*
)
set "EXITCODE=%ERRORLEVEL%"
popd

exit /b %EXITCODE%
