# Neo4j 5 + Graph Data Science (Community) 2.13.4. The GDS jar is fetched
# in a small builder stage and copied into the final image so that when a
# container started from this image opens its Bolt port, the plugin is
# already on disk under /plugins — Testcontainers' default readiness probe
# waits for Bolt and is therefore sufficient.
#
# Why not NEO4J_PLUGINS=["graph-data-science"]? That env var triggers a
# runtime download from the Neo4j plugin marketplace during container
# start, and GitHub Actions runners observe intermittent failures: Bolt
# opens before the download completes, the test starts, and
# CALL gds.graph.drop(...) hits a "procedure not found" error. Baking the
# JAR into an image layer removes that race entirely.

# ---- builder: pull + verify the JAR -----------------------------------------
FROM alpine:3.20 AS gds-fetcher

ARG GDS_VERSION=2.13.4
# Pinned checksum — an upstream re-publish or supply-chain swap fails the
# build loudly rather than silently shipping a different binary. Refresh
# alongside any GDS_VERSION bump:
#   curl -fsSL <jar-url> | sha256sum
ARG GDS_SHA256=10e072f73992224f1159f246c9d6a89da5f3b3434aeffa5be42647edda13a8d8

RUN apk add --no-cache curl ca-certificates \
 && curl -fsSL -o /tmp/neo4j-graph-data-science.jar \
        "https://github.com/neo4j/graph-data-science/releases/download/${GDS_VERSION}/neo4j-graph-data-science-${GDS_VERSION}.jar" \
 && echo "${GDS_SHA256}  /tmp/neo4j-graph-data-science.jar" | sha256sum -c -

# ---- runtime: stock neo4j + the verified JAR --------------------------------
FROM neo4j:5-community

COPY --from=gds-fetcher /tmp/neo4j-graph-data-science.jar \
                        /var/lib/neo4j/plugins/neo4j-graph-data-science.jar

# GDS procedures need explicit allow-listing under Neo4j 5. Baking the
# config in the image keeps every consumer honest (tests, compose, future
# prod) without duplicating env-vars at the call site.
ENV NEO4J_dbms_security_procedures_unrestricted="gds.*"
ENV NEO4J_dbms_security_procedures_allowlist="gds.*"
