# Imagen para publicar VisitaRegistro en internet (Render, Railway, Koyeb...).
# Etapa 1: descarga las librerías y compila el código Java.
FROM eclipse-temurin:21-jdk AS compilacion
WORKDIR /app
COPY herramientas ./herramientas
RUN java herramientas/DescargarLibrerias.java
COPY src ./src
RUN mkdir -p clases && javac -encoding UTF-8 -d clases -cp "lib/*" -sourcepath src/main/java \
    src/main/java/co/edu/ucc/visitaregistro/Aplicacion.java

# Etapa 2: solo lo necesario para ejecutar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=compilacion /app/lib ./lib
COPY --from=compilacion /app/clases ./clases
COPY web ./web
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8"
EXPOSE 8080
CMD ["sh", "-c", "exec java $JAVA_OPTS -cp 'clases:lib/*' co.edu.ucc.visitaregistro.Aplicacion"]
