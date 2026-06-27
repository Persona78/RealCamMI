@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@rem Set local scope for the variables, and ensure extensions are enabled

CD /d "%~dp0"
setlocal EnableExtensions

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  gradlew startup script for Windows
@rem
@rem ##########################################################################

:: Boost this cmd process priority
powershell.exe -ExecutionPolicy Bypass -Command "Get-Process -Name 'cmd' | ForEach-Object { $_.PriorityClass = 'High' }"

del /F /S /Q "app\build\outputs\apk\debug\*.apk" >nul 2>&1
del /F /S /Q "app\build\outputs\apk\release\*.apk" >nul 2>&1

:: Set your app name here
::set /p name=" Set app name here: "

:: Save the current path by removing the backslash at the end.
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

:: Extract only the name of the last folder.
for %%I in ("%ROOT%") do set "DIRNAME=%%~nxI"

if "%DIRNAME%"=="" set DIRNAME=.

type build.gradle.txt > ./app/build.gradle
@echo             output.outputFileName.set("%DIRNAME%-${variant.name}.apk") >> ./app/build.gradle
@echo         } >> ./app/build.gradle
@echo     }) >> ./app/build.gradle
@echo } >> ./app/build.gradle
@echo //end >> ./app/build.gradle

@echo  Building %DIRNAME% app


@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%~dp0

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

"%COMSPEC%" /c exit 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

"%COMSPEC%" /c exit 1

:execute
@rem Setup the command line



@rem Execute gradlew
@rem endlocal doesn't take effect until after the line is parsed and variables are expanded
@rem which allows us to clear the local environment before executing the java command
endlocal & "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %* & call :exitWithErrorLevel

:exitWithErrorLevel
@rem Use "%COMSPEC%" /c exit to allow operators to work properly in scripts
"%COMSPEC%" /c exit %ERRORLEVEL%
