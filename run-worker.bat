@echo off
REM Pokreni radnu stanicu. Argumenti: serverHost serverPort parallelJobs [--headless]
java -Dfile.encoding=UTF-8 -cp bin rs.ac.bg.etf.kdp.worker.WorkerMain %*
