@echo off
rem UploadSSHFile CLI launcher -- shipped inside the plugin package (static content, byte-identical everywhere).
rem Do not edit: the plugin integrity check restores this exact file (missing/tampered/stale).
rem Java resolution order: 1) java-home.txt beside this bat (written by the IDEA process, its own JBR)
rem                      2) %JAVA_HOME%\bin\java.exe  3) java on PATH.
rem D-44: if the IDEA runtime record beside this bat is absent, the launcher prints one line
rem - restart IntelliJ IDEA to run this task - and stops; it does not hunt for another Java.
rem D-43: every candidate that is used is version-checked (Java 21+, class file 65) and skipped if older;
rem if no candidate qualifies this launcher prints a plain message and exits with code 12.
rem lib = %~dp0lib ; IDEA config dir derived from %~dp0.
setlocal
set "JAVA="
set "JHB="
if exist "%~dp0java-home.txt" set /p JHB=<"%~dp0java-home.txt"
if defined JHB goto probehit
echo [UploadSSHFile CLI] Please restart IntelliJ IDEA to run this task.
if not defined NO_PAUSE pause
exit /b 12
:probehit
call :check "%JHB%"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :check "%JAVA_HOME%\bin\java.exe"
if not defined JAVA call :check java
if defined JAVA goto run
echo [UploadSSHFile CLI] ERROR: no Java 21+ runtime found. Checked: java-home.txt, JAVA_HOME, PATH.
echo   java-home.txt = "%JHB%"
echo   JAVA_HOME     = "%JAVA_HOME%"
echo   Fix A: start IntelliJ IDEA once, or open the "Upload SSH File" context menu once (no restart needed); the plugin then writes java-home.txt with IDEA's own Java 21+ runtime.
echo   Fix B: point JAVA_HOME to a JDK 21+ install (or put its bin first on PATH), then run again.
if not defined NO_PAUSE pause
exit /b 12
:run
set "CFG=%~dp0..\..\uploadsshfile"
"%JAVA%" -cp "%~dp0lib\*" com.openxt.uploadsshfile.cli.Main --config-dir "%CFG%" %*
set "EC=%ERRORLEVEL%"
echo %* | findstr /c:"--keep-open" >nul 2>&1 && set "KEEP=1"
if not defined NO_PAUSE if not "%EC%"=="0" if not defined KEEP pause
exit /b %EC%
:check
set "CAND=%~1"
if not defined CAND exit /b 0
if not "%CAND%"=="java" if not exist "%CAND%" exit /b 0
set "SPEC="
for /f "tokens=1,3" %%a in ('"%CAND%" -XshowSettings:properties -version 2^>^&1') do if "%%a"=="java.specification.version" set "SPEC=%%b"
if not defined SPEC exit /b 0
set "MAJ="
for /f "tokens=1 delims=." %%a in ("%SPEC%") do set "MAJ=%%a"
if not defined MAJ exit /b 0
if "%MAJ%"=="1" exit /b 0
if %MAJ% LSS 21 exit /b 0
set "JAVA=%CAND%"
exit /b 0
