@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information.
@REM The ASF licenses this file to you under the Apache License, Version 2.0.
@REM ----------------------------------------------------------------------------
@echo off
setlocal
set "MAVEN_PROJECTBASEDIR=%~dp0"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"
for /f "tokens=2 delims==" %%A in ('findstr /b "distributionUrl=" "%WRAPPER_PROPERTIES%"') do set "DISTRIBUTION_URL=%%A"
for %%A in ("%DISTRIBUTION_URL%") do set "ARCHIVE_NAME=%%~nxA"
set "MAVEN_VERSION=%ARCHIVE_NAME:-bin.zip=%"
set "MAVEN_VERSION=%MAVEN_VERSION:apache-maven-=%"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%-bin\octopus\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $archive=Join-Path $env:TEMP '%ARCHIVE_NAME%'; Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile $archive; New-Item -ItemType Directory -Force -Path (Split-Path '%MAVEN_HOME%') | Out-Null; Expand-Archive -Path $archive -DestinationPath (Split-Path '%MAVEN_HOME%') -Force"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%

