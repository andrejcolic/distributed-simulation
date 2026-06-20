@echo off
REM Pokreni centralni server. Argument: [serverPort]
java -Dfile.encoding=UTF-8 -cp bin rs.ac.bg.etf.kdp.server.ServerMain %*
