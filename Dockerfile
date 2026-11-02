# 공개 시연 서버(Render 등)용 이미지(AC-12.7).
# 시연 모드로 띄운다: 서버 키 없이 동작하고, 방문자가 자기 Gemini 키를 넣었을 때만 그 요청에 그 키로 Gemini를 부른다(FR-12).
# 키는 이미지에도 설정에도 넣지 않는다. 측정 모드는 끈다.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/prompt-gateway-0.0.1-SNAPSHOT.jar app.jar
ENV GATEWAY_DEMO_ANSWER=true \
    GATEWAY_MEASUREMENT_MODE=false
EXPOSE 8080
# 클라우드가 정해 주는 포트(PORT)가 있으면 그 포트로, 없으면 8080으로 듣는다
CMD ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT:-8080}"]
