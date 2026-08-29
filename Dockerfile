# ARInsideJ runtime image.
#
# This image is NOT built from source. ARInsideJ depends on the proprietary BMC AR System
# Java API (arapi / arlogger), which is not redistributable and not on any public Maven
# repository - so a public `mvn package` is impossible. Instead this wraps a pre-built
# self-contained fat jar (target/arinsidej.jar), which already bundles those two jars.
#
# Build (after `mvn -o package`, which needs the BMC jars in your local ~/.m2):
#   docker build -t arinsidej .
#
# Run against a live server, writing the site to ./out on the host:
#   docker run --rm \
#     -v "$PWD/out:/data/out" \
#     -v "$PWD/settings.ini:/data/settings.ini:ro" \
#     arinsidej -i /data/settings.ini -s myserver -l Demo -p secret -o /data/out
#
# Run fully offline against an .xml / .def export:
#   docker run --rm \
#     -v "$PWD:/data" \
#     arinsidej -i /data/settings.ini
#
# Published images: ghcr.io/ljlongwing/arinsidej:<version> and :latest (see
# .github/workflows/docker-publish.yml).

FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="ARInsideJ"
LABEL org.opencontainers.image.description="BMC AR System workflow documentation generator (Java port of ARInside)"
LABEL org.opencontainers.image.source="https://github.com/ljlongwing/ARInsideJ"
LABEL org.opencontainers.image.licenses="GPL-2.0-only"

WORKDIR /data
COPY target/arinsidej.jar /opt/arinsidej/arinsidej.jar

ENTRYPOINT ["java", "-jar", "/opt/arinsidej/arinsidej.jar"]
CMD ["--help"]
