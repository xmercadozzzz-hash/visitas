@echo off
rem Compila y ejecuta VisitaRegistro en Windows. Necesita Java 21 (JDK).
rem Luego abre http://localhost:8080 en el navegador.
chcp 65001 > nul
cd /d "%~dp0"

java herramientas\DescargarLibrerias.java || goto error

if exist build\clases rmdir /s /q build\clases
mkdir build\clases
javac -encoding UTF-8 -d build\clases -cp "lib\*" -sourcepath src\main\java src\main\java\co\edu\ucc\visitaregistro\Aplicacion.java || goto error

java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "build\clases;lib\*" co.edu.ucc.visitaregistro.Aplicacion
goto :eof

:error
echo.
echo No se pudo iniciar VisitaRegistro.
echo Revisa que tengas instalado Java 21 (JDK) y conexion a internet la primera vez.
pause
