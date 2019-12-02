FROM openjdk:11-jdk-slim

ARG JAR_FILE=census-rm-action-worker*.jar
CMD ["/usr/local/openjdk-11/bin/java", "-jar", "/opt/census-rm-action-worker.jar"]

COPY healthcheck.sh /opt/healthcheck.sh
RUN chmod +x /opt/healthcheck.sh

RUN groupadd --gid 999 actionworker && \
    useradd --create-home --system --uid 999 --gid actionworker actionworker
USER actionworker

COPY target/$JAR_FILE /opt/census-rm-action-worker.jar
