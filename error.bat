@echo off
setlocal enabledelayedexpansion

for /f "delims=" %%i in ('gh run list --limit 1 --json databaseId -q ".[0].databaseId" 2^>nul') do set LAST_ID=%%i

if "%LAST_ID%"=="" (
    echo No runs found
    goto :end
)

for /f "delims=" %%i in ('gh run view %LAST_ID% --json conclusion -q ".conclusion" 2^>nul') do set LAST_STATUS=%%i

if "%LAST_STATUS%"=="success" (
    echo Last build success, no error
    goto :end
)

if not exist error-logs mkdir error-logs

gh run view %LAST_ID% --log-failed 2>nul | findstr /r "^build.*e: " > temp_raw.txt

if not exist temp_raw.txt (
    echo No errors caught
    goto :end
)

powershell -Command "(Get-Content temp_raw.txt) -replace '.*Z ', '' | Set-Content error-logs\error.txt"
del temp_raw.txt

for /f %%c in ('find /c /v "" ^< error-logs\error.txt') do set COUNT=%%c

if "%COUNT%"=="0" (
    echo No errors caught
    goto :end
)

echo %COUNT% error^(s^) found
echo File: error-logs\error.txt
echo Time: %date% %time%
echo.
type error-logs\error.txt
echo.

powershell -Command "Get-Content error-logs\error.txt -Raw | Set-Clipboard"
echo Copied to clipboard!

:end
pause
