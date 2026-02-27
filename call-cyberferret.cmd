@echo off
setlocal

if "%CYBER_FERRET_PATH%"=="" (
  echo 'CYBER_FERRET_PATH' System Variable is not set. 1>&2
  echo It must point to path of 'https://github.com/exadmin/CyberFerret' repository clone
  exit /b 1
)

if not exist "%CYBER_FERRET_PATH%/cyberferret.jar" (
  echo File '%CYBER_FERRET_PATH%/cyberferret.jar' does not exist. 1>&2
  echo "Also, compile & build project before use, probably run 'mvn clean package assembly:single' but better see README.md" 1>&2
  exit /b 1
)

call javaw -cp %CYBER_FERRET_PATH%/cyberferret.jar com.github.exadmin.cyberferret.CyberFerretCLI %CD%

