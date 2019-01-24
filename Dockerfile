#FROM ubuntu:latest
FROM java:8

RUN mkdir /opt/docker-ci-prototype/
COPY build/libs/docker-ci-prototype-capsule.jar /opt/docker-ci-prototype/
CMD ["java", "-jar", "/opt/docker-ci-prototype/docker-ci-prototype-capsule.jar", "server"]

