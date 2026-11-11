#!/bin/sh
# Compila y ejecuta VisitaRegistro en Linux o macOS. Necesita Java 21 (JDK).
# Luego abre http://localhost:8080 en el navegador.
set -e
cd "$(dirname "$0")"

java herramientas/DescargarLibrerias.java

rm -rf build/clases
mkdir -p build/clases
javac -encoding UTF-8 -d build/clases -cp "lib/*" -sourcepath src/main/java \
  src/main/java/co/edu/ucc/visitaregistro/Aplicacion.java

exec java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "build/clases:lib/*" co.edu.ucc.visitaregistro.Aplicacion
