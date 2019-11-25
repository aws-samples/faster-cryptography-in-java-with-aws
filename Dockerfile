FROM amazoncorretto:8
VOLUME /tmp
COPY BOOT-INF/lib /app/lib
COPY META-INF /app/META-INF
COPY BOOT-INF/classes /app
ENTRYPOINT java $JVM_MAX_HEAP_SIZE -cp app:app/lib/* com.amazonaws.fcj.FasterCryptographyInJavaService
