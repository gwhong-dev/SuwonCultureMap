# =====================================================
#  내 PC 용 Jenkins 이미지 — Jenkins(최신 LTS) + 빌드용 JDK 17 + Node 22 + Docker CLI · compose
#  실행: scripts\jenkins-start.cmd (docker compose -f jenkins/docker-compose.yml up -d --build)
# =====================================================
FROM node:22-bookworm-slim AS node
FROM docker:27-cli AS docker
FROM eclipse-temurin:17-jdk AS jdk17

# Jenkins 본체는 최신 LTS(JDK 21) — 플러그인이 최신 Jenkins 를 요구해도 설치가 깨지지 않게
FROM jenkins/jenkins:lts-jdk21

USER root
# 빌드용 JDK 17 — GitHub Actions(setup-java temurin 17)와 같은 버전으로 컴파일 · 테스트
COPY --from=jdk17 /opt/java/openjdk /opt/jdk17
# Node — Jenkinsfile "화면 표시 규칙 시험" (node --test)
COPY --from=node /usr/local/bin/node /usr/local/bin/node
# Docker CLI + compose 플러그인 — 이미지 빌드 · compose 설정 확인 (실제 Docker 는 PC 것을 docker.sock 으로 씀)
COPY --from=docker /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker /usr/local/libexec/docker/cli-plugins/docker-compose /usr/local/libexec/docker/cli-plugins/docker-compose
COPY --from=docker /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx

# 플러그인 미리 설치 + 설정 자동화(JCasC) → 처음 켤 때 설치 마법사 없이 바로 작업 화면
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt
COPY casc.yaml /usr/share/jenkins/ref/casc.yaml
ENV CASC_JENKINS_CONFIG=/usr/share/jenkins/ref/casc.yaml \
    JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Duser.timezone=Asia/Seoul"
