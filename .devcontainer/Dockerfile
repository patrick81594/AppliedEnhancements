# Dev container for the Applied Enhancements 26.1.2 port.
# JDK 25 on Ubuntu 24.04 "noble" (tag verified in Phase 1, Step 5a).
FROM eclipse-temurin:25-jdk-noble

# Tools the base image lacks: git (VS Code Git, build metadata) and sudo.
# One RUN = one layer; deleting the apt lists in the same layer keeps them
# out of the image.
RUN apt-get update \
 && apt-get install -y --no-install-recommends git sudo \
 && rm -rf /var/lib/apt/lists/* \
 && echo "ubuntu ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/ubuntu \
 && chmod 0440 /etc/sudoers.d/ubuntu

# Pre-create the Gradle cache folder owned by ubuntu. An empty named volume
# mounted here copies this folder's ownership on first use; without it the
# volume would be owned by root and Gradle would fail.
RUN mkdir -p /home/ubuntu/.gradle && chown ubuntu:ubuntu /home/ubuntu/.gradle

# Existing user in the base image, UID/GID 1000 = WSL user harveyp.
USER ubuntu
