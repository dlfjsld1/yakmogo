# 인증과 인가 트러블슈팅

이 문서는 Goal 2에서 실제 코드, 테스트 실패와 배포 검증으로 확인한 인증 문제를 기록한다.

## 사례 1: 값이 있기만 하면 통과하던 `x-magic-token`

### 상황

React 관리자 웹과 Telegram 링크 사용자가 같은 `/api/**` 경로를 호출하고 있었다. Goal 2의 목적은 두 인증 방식을 구분하고 Telegram 사용자에게 관리 대상 userId 범위만 허용하는 것이었다.

### 최초 접근

변경 전 `AdminInterceptor`는 관리자 비밀번호가 일치하면 요청을 허용하고, 그렇지 않더라도 `x-magic-token` 헤더가 비어 있지 않으면 요청을 허용했다.

```java
if (adminPassword.equals(requestPassword)) return true;
if (magicToken != null && !magicToken.isEmpty()) return true;
```

`AuthController`가 UUID를 발급했지만 서버는 그 UUID를 저장하거나 검증하지 않았다.

당시 의도 확인 불가: UUID 형태의 값을 사용하면 토큰처럼 보이기 때문에 충분하다고 판단했는지는 Git 이력만으로 확인할 수 없다.

### 발생한 증상

운영 장애 로그가 아니라 코드 검토로 확인한 취약점이다. 공격자는 다음처럼 임의 문자열만 넣어 보호 API를 통과할 수 있었다.

```http
x-magic-token: any-non-empty-string
```

### 조사 과정

1. `AdminInterceptor`의 헤더 검사 조건을 확인했다.
2. `AuthController`가 발급한 UUID가 저장·서명·검증되는 위치를 검색했다.
3. 서버에 토큰 검증 근거가 전혀 없음을 확인했다.
4. 사용자·약·복용 API가 동일 인터셉터 뒤에 있어 임의 토큰으로 변경 API까지 접근 가능함을 확인했다.

### 원인

토큰을 “서버가 확인할 수 있는 자격 증명”이 아니라 “값이 있는 헤더”로 취급했다. 인증 성공 여부와 접근 가능한 userId 범위도 분리되어 있지 않았다.

### 해결

커밋 `b388ee1`에서 다음을 적용했다.

- HMAC-SHA256 서명 access token
- 1시간 만료
- 토큰 payload의 `allowedUserIds`
- 최소 인증 주체 `AuthenticatedPrincipal`
  - `principalType`
  - `allowedUserIds`
  - `admin`
- request 범위 `AuthenticationContext`
- 서비스 계층의 `AuthorizationService.requireAdmin()`과 `requireUserAccess(userId)`
- 인증 실패 `401`, 인증됐지만 범위 밖이면 `403`

토큰 형식은 다음처럼 단순하게 유지했다.

```text
base64url(payload).base64url(HMAC-SHA256(payload))
```

별도 역할 계층이나 Android 전용 주체 유형은 추가하지 않았다.

### 검증

`AuthIntegrationTests`와 8081 통합 요청에서 다음을 확인했다.

| 요청 | 결과 |
|---|---:|
| 임의 `x-magic-token`으로 `/api/v1/users` | 401 |
| 발급 범위에 포함된 userId 조회 | 200 |
| 다른 userId 조회 | 403 |
| 다른 사용자의 약 삭제 | 403 |
| 다른 사용자의 복용 완료 | 403 |
| Telegram 토큰으로 관리자 전체 목록 조회 | 403 |

다른 사용자의 약과 복용 기록은 403 이후에도 원래 상태임을 저장소 조회로 검증했다.

### 배운 점

- 랜덤해 보이는 문자열을 발급하는 것과 서버가 토큰을 검증하는 것은 다른 문제다.
- 인증과 인가는 분리해야 한다. “누구인가”를 확인한 다음 “어느 userId까지 가능한가”를 확인해야 한다.
- 컨트롤러 경로에 userId가 없더라도 약 ID나 복용 기록 ID에서 소유 userId를 찾은 뒤 검사해야 한다.

### 재발 방지

- `rejectsArbitraryMagicToken()`
- `telegramTokenIsLimitedToIssuedUserIds()`
- `telegramTokenCannotMutateAnotherUsersMedicineOrIntake()`
- feature 브랜치의 Backend CI에서 모든 테스트 실행

## 사례 2: Chat ID를 알면 로그인할 수 있던 링크

### 상황

변경 전 Telegram 상세 링크는 다음 형태였다.

```text
/tg-login?chatId=<telegram-chat-id>
```

React는 이 값을 `/api/v1/auth/telegram?chatId=...`에 전달했고, 백엔드는 해당 Chat ID가 보호자로 등록되어 있으면 access token 역할의 UUID를 반환했다.

### 최초 접근

Chat ID를 Telegram 사용자 식별자로 사용했다. 데이터베이스에서 보호자와 관리 사용자를 연결하기에는 충분했지만, Chat ID를 알고 있다는 사실을 본인 인증 증명으로도 사용했다.

### 발생한 증상

실제 침해 로그는 확인되지 않았다. 코드 경로를 분석해 다음 문제가 확정됐다.

- Chat ID 또는 링크를 아는 사람은 누구나 인증 API를 호출할 수 있다.
- URL query의 Chat ID가 브라우저 기록이나 접근 로그에 남을 수 있다.
- 발급된 UUID도 서버 검증이 없었다.

### 조사 과정

1. `TelegramService.createInlineKeyboard()`가 Chat ID를 URL에 넣는 것을 확인했다.
2. `TelegramLoginHandler`가 query를 그대로 백엔드에 전달하는 것을 확인했다.
3. `AuthController`가 별도 증명 없이 Chat ID로 사용자를 조회하는 것을 확인했다.

### 원인

식별자와 인증 증명을 같은 것으로 취급했다. Chat ID는 “누구를 찾을지” 알려주지만 요청자가 그 사람임을 증명하지 않는다.

### 해결

커밋 `b388ee1`, `a5f281d`에서 다음 흐름으로 변경했다.

```text
TelegramService
  └─ 5분 만료 HMAC 로그인 proof 생성
       └─ /tg-login?proof=...
            └─ React가 POST /api/v1/auth/telegram으로 proof 교환
                 └─ 서버가 서명·만료 검증
                      └─ DB에서 Chat ID의 allowedUserIds 조회
                           └─ 1시간 access token 발급
```

React는 proof를 읽은 직후 `history.replaceState`로 주소 표시줄에서 제거한다. 인증 API 호출은 query가 아니라 JSON body를 사용한다.

### 검증

- 변조한 proof는 401
- 로그인 proof를 access token으로 사용하면 401
- access token을 로그인 proof로 사용할 수 없음
- 8081에서 서버 내부 서명 키로 proof를 생성해 실제 교환 성공
- 교환된 토큰으로 허용 사용자 200, 다른 사용자 403
- 통합 테스트용 사용자는 검증 후 삭제했고 잔여 개수 0 확인

### 배운 점

- 식별자는 비밀이 아니다.
- URL에 자격 증명이 필요한 경우 짧은 만료와 서명이 필요하고, 사용 후 주소에서 제거해야 한다.
- 백엔드와 프런트의 인증 계약을 동시에 바꾸고 통합 테스트해야 한다.

### 재발 방지

- 기존 `chatId=` 로그인 문자열을 소스 전체에서 검색해 제거했다.
- `AuthTokenServiceTest.keepsLoginProofSeparateFromAccessToken()` 추가
- 인증 API 자체의 401은 Axios 전역 리다이렉트가 가로채지 않도록 예외 처리

## 사례 3: Java record 접근자와 정적 팩터리 이름 충돌

### 상황

최소 인증 주체를 record로 정의하면서 관리자 주체 생성용 정적 팩터리를 추가했다.

### 최초 접근

record component를 `boolean admin`으로 두고 정적 메서드도 `admin()`으로 작성했다.

### 발생한 증상

컴파일이 다음 오류로 실패했다.

```text
invalid accessor method in record AuthenticatedPrincipal
return type of accessor method admin() must match the type of record component admin
```

### 조사 과정

컴파일러가 record component `admin`에 대해 이미 `boolean admin()` 접근자를 생성한다는 점과 정적 메서드 이름이 충돌한 것을 확인했다.

### 원인

Java record가 자동 생성하는 접근자 이름을 정적 팩터리에 재사용했다.

### 해결

정적 팩터리 이름을 `forAdmin()`으로 변경했다.

### 검증

`compileJava`와 전체 `test`가 통과했다.

### 배운 점

record component 이름은 필드명뿐 아니라 자동 생성 메서드 이름이기도 하다.

### 재발 방지

Backend CI에서 매 feature push마다 `clean test bootJar`를 실행한다.

## 사례 4: 테스트용 보조 생성자로 인한 Spring Bean 생성 실패

### 상황

만료 테스트에서 시간을 고정하기 위해 `AuthTokenService`에 `Clock`을 받는 보조 생성자를 추가했다.

### 최초 접근

운영용 생성자와 package-private 테스트용 생성자를 함께 두었지만 Spring이 사용할 생성자를 명시하지 않았다.

### 발생한 증상

컴파일은 통과했으나 `contextLoads()`가 실패했다.

```text
BeanCreationException
BeanInstantiationException
NoSuchMethodException
```

### 조사 과정

1. 실패가 DB 연결이 아니라 Bean 생성 단계임을 테스트 결과에서 확인했다.
2. 직전에 생성자가 두 개로 늘어난 `AuthTokenService`를 확인했다.
3. Spring이 주입 생성자를 선택하지 못하고 기본 생성자를 찾으려 한 것을 확인했다.

### 원인

생성자가 하나일 때는 생략할 수 있던 주입 표시가 생성자 두 개가 되면서 모호해졌다.

### 해결

운영 생성자에 `@Autowired`를 명시하고 테스트는 package-private 생성자로 고정 `Clock`을 전달했다.

### 검증

- `contextLoads()` 통과
- 고정 시각에서 발급 후 11초 뒤 검증하는 만료 테스트 통과
- 전체 Backend CI 통과

### 배운 점

테스트 가능성을 위해 생성자를 추가할 때 DI 컨테이너의 생성자 선택 규칙도 함께 확인해야 한다.

### 재발 방지

애플리케이션 컨텍스트 테스트와 순수 단위 테스트를 모두 유지한다.

## 사례 5: 관리자 비밀번호를 서명 키 fallback으로 쓰려던 임시 구현

### 상황

고도화 서버에 새 환경변수가 아직 없어서 초기 구현은 `AUTH_TOKEN_SECRET`이 없으면 관리자 비밀번호를 서명 키로 사용하는 fallback을 검토하고 일시 적용했다.

### 최초 접근

기존 배포 설정을 깨지 않고 기능을 먼저 실행할 수 있다는 장점이 있었다.

### 발생한 증상

실행 오류는 없었다. 배포 전 보안 검토에서 짧거나 재사용된 관리자 비밀번호가 HMAC 키가 될 수 있다는 문제가 확인됐다.

### 원인

호환성을 위해 인증 비밀값의 역할을 합치면 한 비밀값의 약점과 유출이 다른 인증 체계로 전파된다.

### 해결

- `AUTH_TOKEN_SECRET`을 필수 설정으로 변경
- `AuthTokenService`에서 32바이트 미만 거부
- 8081 실행 스크립트에 암호학적 난수 48바이트를 최초 1회 생성해 설정
- 실제 키 값은 문서와 로그에 기록하지 않음

### 검증

- 키가 포함된 8081 서비스 정상 시작
- Goal 2 JAR 체크섬 검증 후 배포
- proof 교환과 access token 검증 성공

### 배운 점

서로 다른 보안 목적의 비밀값은 분리해야 한다. “일단 동작하는 fallback”은 보안 설정에서 영구 취약점이 되기 쉽다.

### 재발 방지

- 32바이트 최소 길이 검사
- 테스트 설정에도 명시적 서명 키 사용
- Goal 3에서 systemd 보안 설정으로 이동 예정

## 검토 후 제외한 대안

다음은 실제 실패한 구현이 아니라 Goal 2 설계 중 검토 후 제외한 방식이다.

### DB 세션 테이블

토큰 폐기와 일회성 proof 구현에는 유리하지만 Flyway 기준선이 확정되기 전에 인증 테이블부터 추가하면 Goal 4의 마이그레이션 범위를 침범한다. 현재 Goal에서는 stateless HMAC과 짧은 proof 만료를 선택했다.

### 범용 역할·권한 프레임워크

현재 필요한 주체는 관리자와 Telegram 사용자뿐이다. 사용하지 않는 역할 계층이나 Android 전용 주체를 미리 만들면 현재 규모에 비해 과도하고 후속 요구를 잘못 고정할 수 있어 제외했다.

### FCM 또는 Android 인증 유형

2차 고도화 범위이므로 구현하지 않았다. 현재의 최소 principal은 특정 React 컴포넌트에 종속되지 않지만 미래 클라이언트용 타입이나 API를 미리 추가하지 않았다.

## 알려진 한계

- access token은 stateless라 만료 전 개별 폐기 기능이 없다.
- 5분 안에는 로그인 proof를 다시 사용할 수 있다. 일회성 proof가 필요하면 DB 또는 공유 저장소 설계가 필요하다.
- 관리자 인증은 여전히 정적 비밀번호 헤더 방식이다.
- 8081의 서명 키는 임시로 권한 700인 `start.sh`에 있으며 Goal 3에서 이동해야 한다.
- Goal 2 배포 이후 기존 `chatId` 형식 링크는 고도화 환경에서 동작하지 않는다.
- Telegram callback은 아직 `IntakeLogService`가 아닌 자체 상태 전이 코드를 사용한다. Goal 7에서 공통 명령 경계로 통합한다.

## 근거

- Backend 커밋: `b388ee1`
- Web 커밋: `a5f281d`
- Backend CI: <https://github.com/dlfjsld1/yakmogo/actions/runs/29302209037>
- Web CI: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29302210279>
- 자동 테스트: `AuthTokenServiceTest`, `AuthIntegrationTests`
- 8081 검증: 허용 userId 200, 범위 밖 403, 임의 토큰 401, 임시 사용자 잔여 0
