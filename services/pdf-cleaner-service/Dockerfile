FROM eclipse-temurin:26-jdk

WORKDIR /app

# Remove pre-existing ubuntu user (UID 1000) from Ubuntu 24.04 base if present, then create appuser
RUN touch /var/mail/ubuntu 2>/dev/null || true && \
    (userdel -r ubuntu 2>/dev/null || true) && \
    useradd -m -u 1000 -s /bin/bash appuser && \
    mkdir -p /app /home/appuser/.gradle && \
    chown -R appuser:appuser /app /home/appuser

USER appuser
ENV GRADLE_USER_HOME=/home/appuser/.gradle

# Copy Gradle wrapper and build configuration files
COPY --chown=appuser:appuser gradlew .
COPY --chown=appuser:appuser gradle/ gradle/
COPY --chown=appuser:appuser build.gradle settings.gradle ./

# Ensure wrapper is executable and pre-cache Gradle distribution & dependencies
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy application source code
COPY --chown=appuser:appuser src/ src/

# Expose Spring Boot default HTTP port
EXPOSE 8080

# Run Spring Boot development server
CMD ["./gradlew", "bootRun", "--no-daemon"]

