#!/usr/bin/env bash
# Run a worker. Arguments: serverHost serverPort parallelJobs [--headless]
java -Dfile.encoding=UTF-8 -cp bin worker.WorkerMain "$@"
