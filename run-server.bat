@echo off
REM Run the central server. Argument: [serverPort]
java -Dfile.encoding=UTF-8 -cp bin server.ServerMain %*
