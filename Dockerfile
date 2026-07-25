FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY build/libs/walletwizzard-*.jar app.jar
RUN chown app:app app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xmx400m", "-Xms128m", \
  "-XX:+UseContainerSupport", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
