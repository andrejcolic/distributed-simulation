@echo off
REM Run the user program (client)
java -Dfile.encoding=UTF-8 -cp bin client.ClientMain %*
