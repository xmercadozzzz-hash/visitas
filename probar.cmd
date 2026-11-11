@echo off
rem Ejecuta las pruebas automaticas (JUnit) en Windows.
chcp 65001 > nul
cd /d "%~dp0"

java herramientas\DescargarLibrerias.java pruebas || goto error

if exist build\clases rmdir /s /q build\clases
if exist build\pruebas rmdir /s /q build\pruebas
mkdir build\clases
mkdir build\pruebas
javac -encoding UTF-8 -d build\clases -cp "lib\*" -sourcepath src\main\java src\main\java\co\edu\ucc\visitaregistro\Aplicacion.java || goto error
javac -encoding UTF-8 -d build\pruebas -cp "build\clases;lib\*" src\test\java\co\edu\ucc\visitaregistro\PruebasUnitariasTest.java src\test\java\co\edu\ucc\visitaregistro\PruebasSistemaTest.java || goto error

java -Dstdout.encoding=UTF-8 -cp "build\clases;build\pruebas;lib\*" org.junit.runner.JUnitCore co.edu.ucc.visitaregistro.PruebasUnitariasTest co.edu.ucc.visitaregistro.PruebasSistemaTest
pause
goto :eof

:error
echo.
echo No se pudieron ejecutar las pruebas. Revisa que tengas Java 21 (JDK).
pause
