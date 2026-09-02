# ARInsideJ runtime image.
#
# This image is NOT built from source, and it does NOT contain BMC's AR System Java API.
# ARInsideJ needs the proprietary BMC jars (arapi / arlogger) to run, and those are not
# redistributable, so they are neither compiled in nor shipped in this image. It wraps the
# pre-built application jar (target/arinsidej.jar) and expects the BMC jars to be provided
# at runtime on a volume mounted at /opt/arinsidej/lib.
#
# Build (after `mvn -o package`, which needs the BMC jars in your local ~/.m2):
#   docker build -t arinsidej .
#
# Run against a live server. Mount a folder holding arapi*.jar / arlogger*.jar onto
# /opt/arinsidej/lib (see lib/README.txt for where to get them), plus your work dir:
#   docker run --rm \
#     -v "$PWD/lib:/opt/arinsidej/lib:ro" \
#     -v "$PWD/out:/data/out" \
#     -v "$PWD/settings.ini:/data/settings.ini:ro" \
#     arinsidej -i /data/settings.ini -s myserver -l Demo -p secret -o /data/out
#
# Run fully offline against an .xml / .def export (still needs arapi in /opt/arinsidej/lib):
#   docker run --rm \
#     -v "$PWD/lib:/opt/arinsidej/lib:ro" \
#     -v "$PWD:/data" \
#     arinsidej -i /data/settings.ini
#
# Prefer a self-contained image? Write your own:
#   FROM ghcr.io/ljlongwing/arinsidej:latest
#   COPY my-arapi.jar my-arlogger.jar /opt/arinsidej/lib/
# Keep that image private - it contains BMC's proprietary jars.
#
# Published base images: ghcr.io/ljlongwing/arinsidej:<version> and :latest (see
# .github/workflows/docker-publish.yml).

FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="ARInsideJ"
LABEL org.opencontainers.image.description="BMC AR System workflow documentation generator (Java port of ARInside)"
LABEL org.opencontainers.image.source="https://github.com/ljlongwing/ARInsideJ"
LABEL org.opencontainers.image.licenses="GPL-2.0-only"

WORKDIR /data
COPY target/arinsidej.jar /opt/arinsidej/arinsidej.jar
# Mount point for the caller-supplied BMC jars. Empty in the published image.
RUN mkdir -p /opt/arinsidej/lib

# Java expands the lib/* classpath wildcard itself - no shell needed. Entry point is
# arinside.Main (not arinside.Launch): this same Dockerfile is reused to rebuild images for
# older releases whose jars predate the Launch wrapper class.
ENTRYPOINT ["java", "-cp", "/opt/arinsidej/arinsidej.jar:/opt/arinsidej/lib/*", "arinside.Main"]
CMD ["--help"]
