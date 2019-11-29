FROM openjdk:11-jdk-slim

ARG JAR_FILE=census-rm-action-worker-processor*.jar
CMD ["/usr/local/openjdk-11/bin/java", "-jar", "/opt/census-rm-action-worker-processor.jar"]

COPY healthcheck.sh /opt/healthcheck.sh
RUN chmod +x /opt/healthcheck.sh

RUN groupadd --gid 999 actionworkerprocessor && \
    useradd --create-home --system --uid 999 --gid actionworkerprocessor actionworkerprocessor
USER actionworkerprocessor

COPY target/$JAR_FILE /opt/census-rm-action-worker-processor.jar
