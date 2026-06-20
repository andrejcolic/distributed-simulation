@echo off
REM Compiles the given framework (excluding TestG.java, which does not compile) + your code into bin\
setlocal
set TEST=assignment\public_tests\test_files\test
if exist bin rmdir /s /q bin
mkdir bin
dir /s /b src\*.java %TEST%\src\*.java | findstr /v /i "TestG.java" > sources.txt
javac -encoding UTF-8 -d bin @sources.txt
del sources.txt
echo Build done in bin\
endlocal
