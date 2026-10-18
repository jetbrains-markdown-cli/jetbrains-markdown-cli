# jetbrains-markdown-cli as a container image, for GitLab CI and anything else that runs containers.
#
# The context is a directory with the release archives extracted into it (see AGENTS.md and .github/workflows/build.yml):
#   jetbrains-markdown-cli-linux-amd64/{jetbrains-markdown-cli,LICENSE,THIRD-PARTY}
#   jetbrains-markdown-cli-linux-arm64/{jetbrains-markdown-cli,LICENSE,THIRD-PARTY}
#   podman build -f Containerfile image-context
#
# Only what buildah/podman and Docker (BuildKit) both understand: no `# syntax=`, no RUN (so that the arm64 image
# can be built without emulation), no HEALTHCHECK (podman's OCI format drops it).

# Fully qualified, as podman would otherwise ask which registry "debian" means. The binary is linked to glibc, which
# rules out Alpine, and GitLab CI needs a shell in the image, which rules out scratch and distroless.
FROM docker.io/library/debian:stable-slim

# amd64 or arm64: set by `docker buildx build --platform` and by `podman/buildah build --platform`, from the
# host if there is no --platform.
ARG TARGETARCH
ARG VERSION=dev
ARG REVISION=unknown

COPY jetbrains-markdown-cli-linux-${TARGETARCH}/jetbrains-markdown-cli /usr/local/bin/jetbrains-markdown-cli
COPY jetbrains-markdown-cli-linux-${TARGETARCH}/LICENSE jetbrains-markdown-cli-linux-${TARGETARCH}/THIRD-PARTY /usr/share/doc/jetbrains-markdown-cli/

LABEL org.opencontainers.image.title="jetbrains-markdown-cli" \
      org.opencontainers.image.description="Markdown to HTML like the JetBrains IDEs, a replacement for pandoc" \
      org.opencontainers.image.source="https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli" \
      org.opencontainers.image.url="https://github.com/jetbrains-markdown-cli/jetbrains-markdown-cli" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"

# No ENTRYPOINT and no USER: GitLab CI starts the script of a job with a shell in the image (an ENTRYPOINT would
# have to be reset in every job), and a non-root user often cannot write to the checked-out project.
WORKDIR /work
CMD ["jetbrains-markdown-cli", "--help"]
