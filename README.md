# JCode WebIDE Backend


## 📌 프로젝트 개요

> **이 프로젝트**는 Kubernetes on OpenStack 프로젝트의 연장선입니다.

 이 프로젝트는 학생들이 IP:Port로 직접 WebIDE에 접근하고, 컨테이너 생성·삭제를 수동 요청해야 하는 불편함이 있어 이를 해결하는 동시에 학생들의 데이터를 분석하기 위해 기획되었습니다.

■ **전체 프로젝트**  
- 교육용 WebIDE 제공을 통해 교수자·학생 누구나 어디서든 웹으로 접근 가능  
- 교수자는 각 학생의 과제 수행도를 실시간 대시보드 및 그래프 분석으로 확인  
- 과제 난이도 조정, LLM 사용·이상 행위 탐지 등 학습 품질 관리 시스템 구현  

■ **내 세부 프로젝트**  
- 교육용 WebIDE와 분석 시스템·프론트를 연결하는 미들웨어 백엔드 개발  
- WebIDE(VNC 포함) 컨테이너의 동적 생성·삭제 및 프록시 서비스 구축  
- Keycloak 기반 인증 강화, JWT·Redis 연계 세션 관리로 보안성 확보  


---

## 🔧 기술 스택
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white)
![Spring Boot](https://img.shields.io/badge/SpringBoot-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=flat-square&logo=kotlin&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-43853D?style=flat-square&logo=node.js&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=flat-square&logo=fastapi&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)
![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=flat-square&logo=mariadb&logoColor=white)
![MSA](https://img.shields.io/badge/MSA-Microservices-7D7D7D?style=flat-square)

---


## 🎯 전체 역할 분담

| 역할                              | 담당 내용                                                                                                                                                                |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ◆ PM (1)                          | 프로젝트 총괄 및 일정·자원 관리                                                                                                                                           |
| ◆ Front (1)                       | UI 구성 및 Backend 연계                                                                                                                                                  |
| ◆ **Main Backend & Proxy (2) - ME**   | - 분석 시스템과 프론트 연동 미들웨어 설계·구현<br>- Keycloak 인증 → 자체 JWT 발급·Refresh Token Rotate 구현<br>- UUID 기반 쿠키/파라미터 전송으로 세션 관리 강화<br>- WebIDE(VNC) 컨테이너 생성·삭제 서비스 분리 |
| ◆ Watch WebIDE & Analyze (2)      | 사용자별 활동 로그 수집·분석, 이상 행위 탐지 서비스 개발                                                                                                                   |


---


## 🏗 아키텍처
![JCode Backend Architecture](https://github.com/user-attachments/assets/22dd2ade-49a7-444d-ace2-2b095e463518)

> WebIDE 컨테이너는 MS의 CodeServer(`coder/code-server`) 이미지를 활용·커스터마이징

- **Backend :** 인증·DB·API 게이트웨이
- **Container Generator :** Kubernetes API → 컨테이너 동적 생성·삭제
- **Proxy Server :** Redis 연계 프록시 → WebSocket·VNC 트래픽 재검증, 부하 분산
- **Namespace Management :** 강의별 네임스페이스,네트워크 정책·HPA 설정으로 자원·트래픽 관리
- **스토리지(PV) :** RWX 볼륨 프로비저닝(Longhorn), 컨테이너 데이터 중앙 관리
- **Redis :** 사용자 정보 전달용 캐시, Spring Session 관리, JWT 토큰 블랙리스트 운영


---

## 🚀 실행/배포 가이드

### 1. 환경 변수 및 설정 파일

프로젝트는 Spring Boot의 `application.properties` 기반으로 환경을 설정합니다. 실제 배포/개발 환경에서는 `src/main/resources/application-example.properties` 파일을 참고하여, 민감 정보는 직접 복사해 `application.properties`로 사용하거나 환경 변수로 주입하세요. **절대 민감 정보가 깃허브에 올라가지 않도록 주의!**

> **Tip:** 운영/개발 환경에서는 반드시 `application-example.properties`를 복사해 사용하거나, 환경 변수로 오버라이드하세요.

--


### 2. 실행 방법

#### (1) 로컬 개발

1. **환경 변수 파일 준비**
   - `src/main/resources/application-example.properties`를 복사해 `application.properties`로 사용하거나, 환경 변수로 직접 지정
2. **의존 서비스 실행**  (MariaDB, Redis 등은 docker-compose로 띄우는 것을 권장)
   ```bash
   docker-compose up -d
   ```
3. **프로젝트 빌드 및 실행**
   ```bash
   ./gradlew build
   ./gradlew bootRun
   ```

#### (2) Docker로 실행

1. **이미지 빌드**
   ```bash
   docker build -t jcode-backend:test .
   ```
2. **컨테이너 실행**
   ```bash
   docker run -d --name jcode-backend \
     -p 8080:8080 \
     -v $(pwd)/src/main/resources/application.properties:/app/application.properties \
     jcode-backend:test
   ```
   - 또는 `docker-compose.yaml` 사용

#### (3) Kubernetes로 배포

1. **ConfigMap/Secret 생성**  
   - `k8s/jcode-val-ex.yaml` 파일을 참고해 ConfigMap/Secret을 생성 (민감 정보는 Secret에만!)
   - 예시:
     ```bash
     kubectl apply -f k8s/jcode-val-ex.yaml
     ```
2. **Backend 배포**
   - `k8s/jcode-backend.yaml`로 배포
     ```bash
     kubectl apply -f k8s/jcode-backend.yaml
     ```
3. **Ingress 설정** (Nginx Ingress 사용 시)
    - `k8s/jcode-backend-ingress-ex.yaml` 파일에서 host(도메인), tls-secret 설정 후 배포
    - 예시:
      ```bash
      # tls 설정 시 생성
      # 도메인이 없거나 tls 설정 안할 시 파일에서 tls 섹션 제거
      kubectl create secret tls {tls-secert} -n watcher \
        --cert={pem 경로} \
        --key={key 경로}
      
      kubectl apply -f k8s/jcode-backend-ingress-ex.yaml
      ```
4. **HPA(오토스케일러) 적용 (선택)**
   - 부하 분산이 필요할 경우 `k8s/jcode-backend-hpa.yaml` 적용
     ```bash
     kubectl apply -f k8s/jcode-backend-hpa.yaml
     ```

> **순서:** `jcode-val-ex.yaml` → `jcode-backend.yaml` → (옵션) `jcode-backend-hpa.yaml`

--

### 3. 부속 유틸리티 안내 (`util/secret/`)

#### (1) `generateSecureSecret.kt`
- **용도:**  JWT 등 보안용 시크릿을 안전하게 생성하는 유틸리티입니다.
- **사용법:**
  ```bash
  ./gradlew run -PmainClass=org.jbnu.jdevops.jcodeportallogin.util.secret.generateSecureSecretKt
  ```
  또는 IDE에서 main 함수 실행
  → 32바이트(256비트) 랜덤 시크릿 문자열이 출력됨
  → JWT/Keycloak 등 시크릿 값으로 활용

#### (2) `RedisSessionParserApplication.kt`
- **용도:**  Redis에 저장된 세션 정보를 파싱/분석하는 도구입니다.
- **사용법:**  별도 실행하여 Redis 세션 데이터 구조를 확인하거나, 세션 관련 문제 디버깅 시 활용

> **참고:** 이 두 파일은 서비스 필수 구성요소는 아니며, 운영/개발 편의를 위한 부속 스크립트입니다.

--

### 4. 기타 참고

- **Swagger UI:**  `/index.html` 또는 `/swagger-ui.html`에서 API 문서 확인 가능
- **로그:**  `/logs` 디렉토리에 로그 파일 저장(설정에 따라 다름)
- **DB/Redis 등 외부 서비스는 별도 준비 필요**

---


## 🎬 WebIDE(VNC) Demo

![demo](https://github.com/user-attachments/assets/a048d262-4030-4e95-afd0-7f90c42aecd6)
> _2025.03 ~ 현재 진행 중_


---


## 📝 Lessons Learned

✔ **YAML 직접 전송 프로비저닝** → 컨테이너 배포 지연 최소화  
✔ **강의별 네임스페이스 분리** → 격리성과 관리 효율성 동시 확보 (HPA·네트워크 정책)  
✔ **Redis 연계 이중 인증 프록시** → 초기 인증 후 세션 재검증으로 부하 분산·안정적 인증  


---

## 📎 추가 자료

- **GitHub**  
  - Parent Repo: https://github.com/JBNU-JEduTools/JCode
