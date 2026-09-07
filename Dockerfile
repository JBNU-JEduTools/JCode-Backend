# ---------- Build Stage ----------
FROM docker.io/library/gradle:8.8-jdk21 AS build
WORKDIR /app

# 전체 프로젝트 소스 복사
COPY . .

# 베이스 이미지의 Gradle 8.8을 사용해 wrapper 재다운로드를 피한다.
# Kotlin 컴파일러를 in-process로 실행해 컨테이너 빌드의 daemon 임시 파일 race도 제거한다.
RUN gradle clean build -x test --no-daemon \
    -Pkotlin.compiler.execution.strategy=in-process

# ---------- Run Stage ----------
FROM docker.io/library/eclipse-temurin:21-jre-jammy
WORKDIR /app

# 배포 환경 설정 (prod)
ENV SPRING_PROFILES_ACTIVE=prod

# 임시 파일용 디렉토리 설정 (Spring Boot에서 사용)  - 특정 로그나 파일 유지 시 필요
VOLUME /tmp

# 빌드 단계에서 생성된 JAR 파일 복사 (보통 build/libs/ 하위에 생성됨)
COPY --from=build /app/build/libs/app.jar app.jar

# 컨테이너에서 노출할 포트 (기본적으로 8080)
EXPOSE 8080

# 애플리케이션 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
