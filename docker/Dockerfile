# Imagen base compatible multi-arquitectura
FROM eclipse-temurin:21-jdk

# Etiquetas de metadatos 
LABEL module.name="iagente" \
      module.maintainer="julian.rss.android@gmail.com"

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copia el JAR al contenedor
COPY target/IAgente-0.0.1-SNAPSHOT.jar app.jar

# Exponemos el puerto de la aplicación
EXPOSE 8080

# Variables de entorno para ajustar la JVM
ENV JVM_INITIAL_JAVA_HEAP=256m
ENV JVM_MAX_JAVA_HEAP=1536m
ENV JVM_MAX_MEMORY=2000m
ENV JVM_YOUNG_GENERATION=1g

# Opciones JVM
ENV JAVA_OPTS="-Xms${JVM_INITIAL_JAVA_HEAP} \
    -Xmx${JVM_MAX_JAVA_HEAP} \
    -Xmn${JVM_YOUNG_GENERATION} \
    -XX:MaxRAM=${JVM_MAX_MEMORY} \
    -XX:-TieredCompilation \
    -XX:ReservedCodeCacheSize=240m \
    -XX:+UnlockExperimentalVMOptions \
    --add-opens java.base/java.io=ALL-UNNAMED \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

# Comando de entrada: arranca el JAR con el perfil docker activo
ENTRYPOINT sh -c "java $JAVA_OPTS -Dspring.profiles.active=docker -jar /app/app.jar"
