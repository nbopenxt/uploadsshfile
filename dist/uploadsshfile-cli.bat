@echo off
rem UploadSSHFile CLI launcher -- shipped inside the plugin package (static content, byte-identical everywhere).
rem Do not edit: the plugin integrity check restores this exact file (missing/tampered/stale).
rem Java resolution order: 1) java-home.txt beside this bat (written by the IDEA process, its own JBR)
rem                      2) %JAVA_HOME%\bin\java.exe  3) java on PATH (JDK 21+ required).
rem lib = %~dp0lib ; IDEA config dir derived from %~dp0.
setlocal
set "JAVA="
if exist "%~dp0java-home.txt" set /p JAVA=<"%~dp0java-home.txt"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA set "JAVA=java"
set "CFG=%~dp0..\..\uploadsshfile"
"%JAVA%" -cp "%~dp0lib\*" com.openxt.uploadsshfile.cli.Main --config-dir "%CFG%" %*
set "EC=%ERRORLEVEL%"
echo %* | findstr /c:"--keep-open" >nul 2>&1 && set "KEEP=1"
if not defined NO_PAUSE if not "%EC%"=="0" if not defined KEEP pause
exit /b %EC%
