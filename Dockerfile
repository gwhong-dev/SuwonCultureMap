# =====================================================
#  수원 문화지도 — 앱 이미지 (2단계 빌드)
#  1단계(build): JDK + Maven 으로 jar 만들기
#  2단계(run)  : JRE 만 들어간 가벼운 이미지에 jar 만 복사
# =====================================================

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 목록(pom.xml)이 안 바뀌면 이 층은 캐시를 재사용 → 다시 빌드할 때 빠름
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# 소스 복사 후 jar 빌드 (테스트는 CI 단계에서 따로 실행)
COPY src/ src/
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# root 가 아닌 전용 사용자로 실행 (보안)
RUN useradd --system --uid 10001 spring
COPY --from=build /app/app.jar app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
