# DrawCen

**실시간 공유 화이트보드 게임**

## 프로젝트 소개

DrawCen은 사용자들이 실시간으로 그림을 그리고 공유할 수 있는 화이트보드 게임 플랫폼입니다. 사용자들은 그림을 그리고 다른 사용자들은 이를 맞추는 형태의 소셜 게임을 즐길 수 있습니다.

## 기술 스택

- **백엔드**: Java 17, Spring Boot 3.4.5
- **데이터베이스**: MongoDB, PostgreSQL, Redis
- **보안**: Spring Security, OAuth2, JWT
- **통신**: WebSocket
- **기타**: Lombok, Swagger

## 시작하기

### 필수 조건

- Java 17 이상
- Gradle
- Docker (MongoDB, PostgreSQL, Redis 컨테이너 실행용)

### 설치 및 실행

```shell
docker-compose -f drawcen-dev.yml up -d 
```

## 환경 설정

프로젝트는 `.env` 파일을 사용하여 환경 변수를 구성합니다. 다음과 같은 변수를 설정해야 합니다:

```properties
# 스프링 프로파일
SPRING_PROFILES_ACTIVE=dev
# PostgreSQL 데이터베이스 포트 (기본값: 5432)
POSTGRESQL_DATABASE_PORT=5432
# PostgreSQL 데이터베이스 이름
POSTGRESQL_DATABASE_NAME=drawcen
# 데이터베이스 호스트 주소 (예: localhost 또는 IP 주소)
DATABASE_HOST=localhost
DEV_DATABASE_HOST=localhost
PROD_DATABASE_HOST=
# PostgreSQL 데이터베이스 접속 사용자명
DB_USERNAME=yagumyagum
PROD_DB_USERNAME=
# PostgreSQL 데이터베이스 접속 비밀번호
DB_PASSWORD=yagumyagum
PROD_DB_PASSWORD=
# MongoDB 관리자 계정 사용자명
MONGO_INITDB_ROOT_USERNAME=root
# MongoDB 관리자 계정 비밀번호
MONGO_INITDB_ROOT_PASSWORD=root
# MongoDB 연결 문자열
MONGODB_URI=localhost:27017/whiteboard?authSource=admin
# CORS 허용 출처 목록 (쉼표로 구분된 URL 목록)
CORS_ALLOWED_ORIGINS=http://localhost:*
# Redis 서버 호스트 주소
REDIS_HOST=localhost
DEV_REDIS_HOST=localhost
PROD_REDIS_HOST=
# Redis 서버 포트 (기본값: 6379)
REDIS_PORT=6379
# Google OAuth 클라이언트 ID
GOOGLE_CLIENT_ID=
# Google OAuth 클라이언트 시크릿
GOOGLE_CLIENT_SECRET=
# Kakao OAuth 클라이언트 ID
KAKAO_CLIENT_ID=
# Kakao OAuth 클라이언트 시크릿
KAKAO_CLIENT_SECRET=
# JWT 토큰 서명에 사용되는 비밀 키 (Base64 인코딩된 값)
JWT_SECRET=
# 액세스 토큰 만료 시간 (초 단위)
ACCESS_TOKEN_EXPIRES_IN=3600
# 리프레시 토큰 만료 시간 (초 단위)
REFRESH_TOKEN_EXPIRES_IN=2592000
# 서버 포트
DEV_SERVER_PORT=8080
PROD_SERVER_PORT=
# 프론트 리다이렉트 경로
FRONTEND_REDIRECT_URL=http://localhost:3000/main
```

## API 문서

API 문서는 다음에서 확인할 수 있습니다:

- [API 문서](docs/API.md)
- Swagger UI: `http://localhost:8080/swagger-ui.html` (서버 실행 후)

## 참여자 목록

| 이름  | 역할      | 기여 내용 | GitHub 프로필                                     |
|-----|---------|-------|------------------------------------------------|
| 기우석 | 백엔드 개발자 |       | [@woosuk1](https://github.com/woosuk1)         |
| 김영권 | 인프라     |       | [@visionn7111](https://github.com/visionn7111) |
| 김유진 | 백엔드 개발자 |       | [@Yoojkim](https://github.com/Yoojkim)         |
| 신채원 | 프론트 개발자 |       | [@chaewon121](https://github.com/chaewon121)   |
| 이승준 | 프론트 개발자 |       | [@Oodls](https://github.com/Oodls)             |
| 임혁  | 백엔드 개발자 |       | [@asimuleo](https://github.com/asimuleo)       |
