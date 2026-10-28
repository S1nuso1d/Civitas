@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 (
    echo ERROR: Could not open the project folder.
    pause
    exit /b 1
)
if not exist ".\tools\gradle-run.ps1" (
    echo ERROR: Missing tools\gradle-run.ps1. Extract the complete project ZIP.
    set "CIVITAS_EXIT=1"
    goto :finish
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File ".\tools\gradle-run.ps1" build
set "CIVITAS_EXIT=%ERRORLEVEL%"
if "%CIVITAS_EXIT%"=="0" (
    echo.
    echo BUILD SUCCESSFUL.
    echo JAR: build\libs\civitas-0.13.0.jar
) else (
    echo.
    echo BUILD FAILED. Review the error messages above.
)
:finish
popd
pause
exit /b %CIVITAS_EXIT%
