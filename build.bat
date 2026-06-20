@echo off
REM Prevodi dati okvir (bez TestG.java koji se ne kompajlira) + tvoj kod u bin\
setlocal
set TEST=assignment\public_tests\test_files\test
if exist bin rmdir /s /q bin
mkdir bin
dir /s /b src\*.java %TEST%\src\*.java | findstr /v /i "TestG.java" > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
del sources.txt
echo Build gotov u bin\
endlocal
