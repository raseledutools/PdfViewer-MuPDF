@echo off
setlocal EnableDelayedExpansion

set "TOOLDIR=%USERPROFILE%\.aitools"

echo ============================================
echo   AI Auto-Fix Build - One Click Setup
echo ============================================
echo.

if not exist "%TOOLDIR%" mkdir "%TOOLDIR%"

if not exist "%~dp0ai-fix-build.ps1" (
    echo [ERROR] ai-fix-build.ps1 ei setup.bat er sathe ekoi folder e pawa jacche na.
    echo Duita file download kore ekoi folder e rakho, tarpor abar try koro.
    pause
    exit /b 1
)

copy /Y "%~dp0ai-fix-build.ps1" "%TOOLDIR%\ai-fix-build.ps1" >nul
echo [OK] Script copied to %TOOLDIR%

REM Create the "ai" command
(
    echo @echo off
    echo powershell -NoProfile -ExecutionPolicy Bypass -File "%%~dp0ai-fix-build.ps1" %%*
) > "%TOOLDIR%\ai.bat"
echo [OK] "ai" command created

REM Token setup (shudhu na thakle jiggesh korbe)
if "%GITHUB_MODELS_TOKEN%"=="" (
    echo.
    echo GitHub Models Token lagbe ekta.
    echo Banaite: https://github.com/settings/tokens -^> Fine-grained token -^> Account permissions -^> Models: Read-only
    echo.
    set /p TOKEN="Token paste kore Enter dao: "
    setx GITHUB_MODELS_TOKEN "!TOKEN!" >nul
    echo [OK] Token save kora hoyeche
) else (
    echo [OK] GITHUB_MODELS_TOKEN already set ache, skip
)

REM Tools folder ke PATH e permanently add kora (user-level, admin lage na)
powershell -NoProfile -Command "$old=[Environment]::GetEnvironmentVariable('Path','User'); if ($old -notlike ('*'+$env:USERPROFILE+'\.aitools*')) { [Environment]::SetEnvironmentVariable('Path', $old+';'+$env:USERPROFILE+'\.aitools', 'User') }"
echo [OK] PATH update kora hoyeche

echo.
echo ============================================
echo   Setup SHESH!
echo   1. Ei CMD window ta BONDHO koro
echo   2. Notun CMD/PowerShell kholo
echo   3. Project folder e cd kore shudhu "ai" likho
echo ============================================
echo.
pause
