@echo off
setlocal
set "ROOT=%~dp0"
set "GRADLE_VERSION=8.10.2"
set "GRADLE_HOME=%ROOT%.gradle\bootstrap\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat"
if not exist "%GRADLE_BIN%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $root='%ROOT%'; $version='%GRADLE_VERSION%'; $dest=Join-Path $root '.gradle\bootstrap'; $zip=Join-Path $dest ('gradle-' + [guid]::NewGuid().ToString() + '.zip'); New-Item -ItemType Directory -Force -Path $dest | Out-Null; Invoke-WebRequest -UseBasicParsing -Uri ('https://services.gradle.org/distributions/gradle-' + $version + '-bin.zip') -OutFile $zip; if ((Get-Item -LiteralPath $zip).Length -lt 1000000) { throw 'Gradle download was incomplete.' }; Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_BIN%" %*
