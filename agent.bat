@echo off
set PATH=%PATH%;C:\Users\Rasel Mahmud\.github-cli
if "%1"=="status" (
    gh run list --limit 5
) else if "%1"=="log" (
    gh run view %2 --log-failed
) else if "%1"=="watch" (
    gh run watch
) else if "%1"=="p" (
    git add .
    git commit -m "auto commit"
    git push origin master
) else (
    %*
)
