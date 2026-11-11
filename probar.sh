#!/bin/sh
# Ejecuta las pruebas automáticas (JUnit) en Linux o macOS.
set -e
cd "$(dirname "$0")"

java herramientas/DescargarLibrerias.java pruebas

rm -rf build/clases build/pruebas
mkdir -p build/clases build/pruebas
javac -encoding UTF-8 -d build/clases -cp "lib/*" -sourcepath src/main/java \
  src/main/java/co/edu/ucc/visitaregistro/Aplicacion.java
javac -encoding UTF-8 -d build/pruebas -cp "build/clases:lib/*" src/test/java/co/edu/ucc/visitaregistro/*.java

java -Dstdout.encoding=UTF-8 -cp "build/clases:build/pruebas:lib/*" org.junit.runner.JUnitCore \
  co.edu.ucc.visitaregistro.PruebasUnitariasTest co.edu.ucc.visitaregistro.PruebasSistemaTest
