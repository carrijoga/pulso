@echo off
setlocal
set "ROOT=%~dp0"
set "WRAPPER_JAR=%ROOT%gradle\wrapper\gradle-wrapper.jar"
rem Gradle 9.6.0 wrapper jar. services.gradle.org publishes only the checksum;
rem the jar itself comes from the release tag.
set "WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar"
set "WRAPPER_SHA=497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"
if exist "%WRAPPER_JAR%" goto run
echo Baixando Gradle Wrapper oficial 9.6.0 com SHA-256 verificado...
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $f=$env:WRAPPER_JAR; $t=$f+'.tmp'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri $env:WRAPPER_URL -OutFile $t; if ((Get-FileHash $t -Algorithm SHA256).Hash.ToLowerInvariant() -ne $env:WRAPPER_SHA) { Remove-Item $t -Force; throw 'Checksum invalido do Gradle Wrapper' }; Move-Item $t $f -Force"
if errorlevel 1 exit /b 1

:run
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java"
)
if defined JAVA_HOME if not exist "%JAVA_HOME%\bin\java.exe" goto nojava
if not defined JAVA_HOME (
  where java >nul 2>nul || goto nojava
)
"%JAVA_EXE%" -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%

:nojava
echo ERRO: Java nao encontrado. Defina JAVA_HOME com JDK 17 ou 21, ou coloque java no PATH. 1>&2
exit /b 1
