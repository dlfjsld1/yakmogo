# Goal 2: 인증·인가 강화

## 한 문장 요약

임의 문자열과 Chat ID만으로 접근할 수 있던 인증을 서명·만료·userId 범위가 있는 최소 토큰 체계로 교체했다.

## 작업 배경

React 관리자 웹은 관리자 비밀번호를, Telegram 링크 사용자는 `x-magic-token`을 사용했다. 그러나 변경 전 서버는 magic token의 값이 있는지만 확인했고 사용자별 권한 범위를 검사하지 않았다.

## 변경 전 문제

- 임의의 non-empty `x-magic-token`이 인증을 통과했다.
- Chat ID를 아는 사람이 Telegram 인증 API를 호출할 수 있었다.
- UUID 토큰에 서명, 만료, 서버 검증이 없었다.
- Telegram 사용자가 다른 userId의 조회·변경 API를 호출할 수 있었다.
- React의 401 처리가 인증 API 실패까지 관리자 로그인 실패로 처리할 수 있었다.

## 이번 Goal 범위

- HMAC 로그인 proof와 access token
- 만료 검증
- 최소 인증 주체와 request 인증 context
- 관리자 여부와 allowed userId 검사
- 사용자, 보호자, 약, 복용 기록 서비스의 권한 보호
- React proof 교환과 401·403 처리
- 단위·통합·CI·8081 실제 검증

## 범위에서 제외한 내용

- 범용 역할·권한 프레임워크
- DB 세션과 token revoke 테이블
- 관리자 비밀번호 방식 전체 교체
- Telegram callback 상태 전이 통합
- Android, Device, Pairing, FCM, Android 인증 API
- 운영 8080과 운영 DB 변경

## 도입한 기술과 개념

- HMAC-SHA256
- Base64 URL-safe encoding
- 5분 로그인 proof
- 1시간 access token
- 최소 `AuthenticatedPrincipal`
- request-scoped `AuthenticationContext`
- 서비스 계층 `AuthorizationService`
- 401 Authentication과 403 Authorization 구분

## 선택한 방식과 대안

### 선택: stateless HMAC

현재 DB 스키마를 바꾸지 않고 서버가 서명과 만료를 검증할 수 있다. Goal 4 Flyway 이전에 인증 테이블을 추가하지 않는다는 범위에도 맞았다.

### 제외: DB session

개별 revoke와 일회성 proof에는 유리하지만 새 테이블과 마이그레이션이 필요해 현재 Goal 범위를 넘는다.

### 제외: 범용 권한 프레임워크

현재 주체는 관리자와 Telegram 사용자 두 종류다. `principalType`, `allowedUserIds`, `admin`만으로 필요한 보호를 구현했고 미래 Android 역할은 추가하지 않았다.

## 실제 동작 흐름

```text
Telegram 알림 생성
  └─ AuthTokenService.issueLoginProof(chatId)
       └─ /tg-login?proof=<5분 서명 proof>
            └─ React가 URL에서 proof 제거
                 └─ POST /api/v1/auth/telegram
                      └─ proof 서명·만료 검증
                           └─ Chat ID로 관리 userId 조회
                                └─ allowedUserIds 포함 1시간 access token

보호 API 요청
  └─ AuthenticationInterceptor
       ├─ 관리자 비밀번호 일치 → admin principal
       └─ access token 검증 → Telegram principal
            └─ 서비스 계층 requireAdmin/requireUserAccess
```

## 변경된 파일과 역할

Backend `b388ee1`:

- `auth/AuthTokenService`: proof·access token 발급과 검증
- `auth/AuthenticatedPrincipal`: 최소 인증 주체
- `auth/AuthenticationContext`: request별 현재 주체
- `auth/AuthorizationService`: 관리자·userId 범위 검사
- `AuthenticationInterceptor`: 헤더 인증과 context 설정
- `AuthController`: proof 교환과 범위 토큰 발급
- `UserService`: 관리자·사용자 범위 적용
- `MedicineGroupService`: 약 소유 userId 검사
- `IntakeLogService`: 복용 기록 소유 userId 검사
- `TelegramService`: Chat ID 링크를 서명 proof 링크로 변경
- `GlobalExceptionHandler`: 401·403 응답
- `AuthTokenServiceTest`, `AuthIntegrationTests`: 보호 시나리오

Web `a5f281d`:

- `TelegramLoginHandler.tsx`: proof JSON 교환, URL 제거, 만료 안내
- `api/http.ts`: 인증 API 401 예외와 일반 401·403 분리

## 데이터 및 설정 변경

DB 테이블은 추가하거나 변경하지 않았다.

새 설정:

```text
AUTH_TOKEN_SECRET=<32-byte-or-longer secret>
AUTH_TOKEN_TTL_SECONDS=3600
AUTH_LOGIN_PROOF_TTL_SECONDS=300
```

8081에는 48바이트 암호학적 난수 서명 키를 설정했다. 실제 값은 기록하지 않는다.

## 시행착오와 문제 해결 과정

### Chat ID 방식도 인증 증명이 아님을 구현 전에 확인

단순히 UUID 검증만 추가하면 Chat ID를 아는 공격자가 정상 토큰을 계속 발급받을 수 있었다. 따라서 access token뿐 아니라 로그인 진입점도 서명 proof로 변경했다.

### record 팩터리 이름 충돌

`boolean admin` component가 자동 생성하는 `admin()`과 정적 팩터리 `admin()`이 충돌해 `compileJava`가 실패했다. 팩터리를 `forAdmin()`으로 변경했다.

### Spring 생성자 선택 실패

고정 Clock 테스트용 생성자를 추가한 뒤 `contextLoads()`가 `NoSuchMethodException`으로 실패했다. 운영 생성자에 `@Autowired`를 명시했다.

### 서명 키 fallback 폐기

초기에는 배포 호환성을 위해 관리자 비밀번호 fallback을 검토하고 일시 적용했지만 약한 키와 비밀값 재사용 문제가 있어 제거했다. `AUTH_TOKEN_SECRET` 필수와 32바이트 최소 검사를 적용했다.

상세 과정: [인증과 인가 트러블슈팅](../troubleshooting/authentication.md)

## 테스트와 보호 시나리오

`AuthTokenServiceTest`:

- 발급 userId 범위 보존
- 변조 토큰 거부
- 만료 토큰 거부
- 로그인 proof와 access token 용도 분리

`AuthIntegrationTests`:

- 임의 magic token 401
- 관리자 접근 200
- 허용 userId 200
- 범위 밖 userId 403
- Telegram token의 관리자 API 403
- 다른 사용자의 약·복용 기록 변경 403 및 상태 불변
- 변조 proof 401

## 배포 및 검증

Goal 2 feature를 8081에만 배포했다.

```text
JAR SHA-256: 50cded494ed06a6fb1f464e2c6068314a5d72dcfd4676cb068f10f639c9d8700
backup: yakmogo-0.0.7-SNAPSHOT.jar.backup-20260714-115720
```

8081 실제 통합 검증:

| 항목 | 결과 |
|---|---:|
| 루트와 새 bundle | 200 |
| 유효 proof 교환 | 성공 |
| 허용 userId | 200 |
| 다른 userId | 403 |
| 임의 token | 401 |
| 임시 테스트 사용자 잔여 | 0 |

운영 8080은 안정 bundle과 HTTP 200을 유지했다.

## 운영 시 주의 사항

- 기존 `?chatId=` 링크는 Goal 2 통합 후 사용할 수 없다.
- 새 링크는 고도화 코드의 `TelegramService`가 생성해야 한다.
- proof도 5분 동안은 bearer credential이므로 로그에 출력하지 않는다.
- access token과 proof 원문을 애플리케이션 로그에 남기지 않는다.
- `AUTH_TOKEN_SECRET`을 변경하면 기존 access token이 모두 무효화된다.

## 직접 확인하는 방법

자동 테스트:

```powershell
cd yakmogo-backend
.\gradlew.bat clean test bootJar --no-daemon

cd ..\yakmogo-web
npm.cmd run lint
npm.cmd test --if-present
npm.cmd run build
```

HTTP 상태 확인 시 실제 비밀값을 명령 이력에 남기지 않도록 주의한다.

```text
인증 없음 또는 임의 token → 401
유효하지만 다른 userId → 403
유효하고 허용된 userId → 200
```

## 알려진 한계

- access token 개별 revoke 불가
- proof는 만료 전 재사용 가능
- 관리자 인증은 정적 비밀번호 헤더
- 8081 secret 저장 방식은 Goal 3에서 개선 필요
- Telegram callback의 상태 전이가 공통 서비스로 아직 통합되지 않음

## 후속 Goal에 넘길 내용

- Goal 3: secret을 systemd EnvironmentFile 또는 credentials로 이동
- Goal 5: 인증 실패·권한 오류 응답 형식과 API 테스트 확대
- Goal 7: REST와 Telegram callback의 복용 완료 경로를 공통 명령 서비스로 통합
- Goal 10: 1차 고도화 후 Android 인증 경계 로드맵 작성

## 근거

- Backend commit: `b388ee1`
- Web commit: `a5f281d`
- Backend CI: <https://github.com/dlfjsld1/yakmogo/actions/runs/29302209037>
- Web CI: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29302210279>
- 상세 사례: [authentication.md](../troubleshooting/authentication.md)
