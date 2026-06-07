@REM Maven Wrapper script for Windows
@echo off
setlocal
set MAVEN_OPTS=%MAVEN_OPTS% -Xmx512m -Dmaven.multiModuleProjectDirectory=%~dp0
java %MAVEN_OPTS% -cp "%~dp0\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*
