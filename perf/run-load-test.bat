@echo off
rem Runs the POST /payments load test in JMeter's non-GUI mode and builds the HTML dashboard.
rem Usage: perf\run-load-test.bat [-Jthreads=5] [-Jloops=20] [-JrampUp=5] [-Jhost=127.0.0.1] [-Jport=8080] [-JmaxResponseMs=500]
rem Set HEALTH_URL when the server isn't at the default address.
setlocal

set PERF_DIR=%~dp0
set PLAN=%PERF_DIR%payments-load-test.jmx
set RESULTS_DIR=%PERF_DIR%results
set RESULTS_FILE=%RESULTS_DIR%\results.jtl
set JMETER_LOG=%RESULTS_DIR%\jmeter.log
set REPORT_DIR=%PERF_DIR%report
if "%HEALTH_URL%"=="" set HEALTH_URL=http://127.0.0.1:8080/health

where jmeter >nul 2>&1
if errorlevel 1 (
  echo error: jmeter not found. Install Apache JMeter and add its bin folder to PATH. 1>&2
  exit /b 1
)

curl.exe -fsS "%HEALTH_URL%" >nul
if errorlevel 1 (
  echo error: the server isn't reachable at %HEALTH_URL%. Start it first, see perf\README.md. 1>&2
  exit /b 1
)

if exist "%REPORT_DIR%" rmdir /s /q "%REPORT_DIR%"
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"
if exist "%RESULTS_FILE%" del "%RESULTS_FILE%"

call jmeter -n -t "%PLAN%" -l "%RESULTS_FILE%" -j "%JMETER_LOG%" -e -o "%REPORT_DIR%" %*
if errorlevel 1 exit /b 1

findstr /c:",false," "%RESULTS_FILE%" >nul
if not errorlevel 1 (
  echo error: some requests failed an assertion; see %REPORT_DIR%\index.html 1>&2
  exit /b 1
)
echo HTML report: %REPORT_DIR%\index.html
echo All requests passed.