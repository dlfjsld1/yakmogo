# Goal 5: 백엔드 API 테스트·검증·상태 전이

## 현재 상태

**구현과 로컬 검증 완료, feature CI와 8081 비파괴 인수 테스트 대기.** `feature/backend-test-suite`에서 작업하며 사용자 승인 전에는 `enhancement`에 병합하지 않는다.

## 한 문장 요약

핵심 API의 성공·실패 계약을 자동 테스트로 고정하고, 입력 검증과 오류 JSON을 통일하며, REST와 Telegram의 복용 완료를 권한·행 잠금·상태 규칙을 공유하는 `IntakeCommandService`로 모았다.

## 작업 배경

Goal 2까지 인증과 사용자 범위 인가는 생겼지만, 기존 테스트는 인증 흐름 중심이었다. 사용자·보호자·약·복용 API의 정상 응답과 실패 상태가 자동화돼 있지 않아 리팩터링 때 React 호환성을 확인하기 어려웠다.

또한 복용 완료가 두 경로에 따로 구현돼 있었다.

```text
REST       -> IntakeLogService -> IntakeLog.markAsTaken()
Telegram   -> repository 직접 조회·변경·save
```

Telegram 경로는 HTTP 인증과 별개라는 이유로 공통 규칙 밖에 있었고, callback을 누른 chat ID가 해당 복용자의 보호자인지도 검사하지 않았다. 두 요청이 동시에 같은 `PENDING` 행을 읽으면 모두 성공할 가능성도 있었다.

## 변경 전 문제

- DTO가 빈 이름, 누락된 일정, 0일 간격을 허용했다.
- 미존재 자원도 `IllegalArgumentException`으로 처리돼 400과 404를 구분하지 못했다.
- 인증 인터셉터, 인증 controller, 전역 예외 처리의 오류 body 형식이 달랐다.
- WEEKLY 계산이 문자열 `contains`를 사용해 `MONDAYX`도 월요일로 오인할 수 있었다.
- Telegram callback이 repository에서 상태를 직접 바꿨다.
- 순차 중복은 일부 막았지만 동시 완료 요청에 대한 행 잠금이 없었다.
- 보호자 삭제 API가 200을 반환해도 영속성 컬렉션 참조 때문에 실제 DELETE가 발생하지 않을 수 있었다.
- H2 테스트만으로 MariaDB enum, `BIT(1)`, FK, 시간 타입을 검증할 수 없었다.

## 이번 Goal 범위

- 사용자 등록·조회·삭제의 성공과 400·401·403·404 테스트
- 보호자 추가·삭제와 타 사용자 보호자 삭제 방지
- 약 등록·수정·중단과 React 일정 payload 호환성 테스트
- DAILY·WEEKLY·INTERVAL 계산 단위 테스트
- DTO Bean Validation과 일정 유형별 교차 검증
- 공통 JSON 오류 모델과 상태 코드 분리
- REST·Telegram 공통 복용 완료 명령 서비스
- 중복·잘못된 상태 전이와 타 chat ID 처리 차단
- 실제 MariaDB CI 검증 범위 확대

## 범위에서 제외한 내용

- React UI 변경(Goal 6)
- scheduler 시각·재시도와 Telegram 발송 정책 변경(Goal 7)
- CI/CD 자동 배포(Goal 8)
- DB migration 추가: 엔티티 관계 동작만 바뀌며 물리 스키마는 동일
- Android, Device, Pairing, FCM API·테이블
- 운영 8080과 운영 `yakmogo` 변경

## 도입한 기술과 개념

### 1. Bean Validation

단순 필드는 `@NotBlank`, `@NotNull`, `@Size`, `@Positive`로 검증한다. 중첩된 보호자 목록은 `List<@Valid GuardianDto>`로 각 원소까지 재귀 검증한다.

약 일정은 필드 하나만 봐서는 유효성을 정할 수 없다.

| scheduleType | 허용 scheduleValue |
|---|---|
| `DAILY` | `null` 또는 빈 문자열 |
| `WEEKLY` | 쉼표로 구분한 실제 요일 1개 이상 |
| `INTERVAL` | 1 이상의 정수 문자열 |

그래서 `@ValidMedicineSchedule` 클래스 수준 제약과 `MedicineScheduleValidator`를 사용한다. 오류 위치는 다시 `scheduleValue` property node에 연결해 클라이언트가 어느 필드를 고쳐야 하는지 알 수 있게 했다.

### 2. 공통 오류 JSON

모든 처리 가능한 오류는 다음 모양을 사용한다.

```json
{
  "code": "VALIDATION_FAILED",
  "error": "입력값이 올바르지 않습니다.",
  "fieldErrors": {
    "scheduleValue": "복용 간격은 1 이상의 정수여야 합니다."
  }
}
```

기존 인증 응답에서 사용하던 `error` 키는 호환성을 위해 유지하고, 기계 판별용 `code`와 선택적 `fieldErrors`를 추가했다.

| 상황 | HTTP | code |
|---|---:|---|
| 잘못된 JSON·입력·상태 전이 | 400 | `INVALID_REQUEST_BODY`, `VALIDATION_FAILED`, `BAD_REQUEST` |
| 인증 없음·잘못된 토큰 | 401 | `UNAUTHORIZED` |
| 다른 사용자·관리자 기능 접근 | 403 | `FORBIDDEN` |
| 사용자·보호자·약·복용 기록 미존재 | 404 | `NOT_FOUND` |

### 3. 명령 서비스와 상태 전이

```text
REST controller ─────┐
                     ├─> IntakeCommandService
Telegram callback ──┘       ├─ 행 잠금 조회
                             ├─ 호출자 권한 확인
                             ├─ PENDING 상태 확인
                             └─ TAKEN + 실제 복용 시각 변경
```

REST는 `AuthorizationService.requireUserAccess()`를 사용한다. Telegram은 callback의 chat ID가 복용자의 guardian인지 `existsByUserIdAndChatId()`로 확인한다. 인증 수단은 다르지만 상태 규칙은 하나다.

`findByIdWithUserAndGroup()`에는 `PESSIMISTIC_WRITE` 잠금을 적용했다. 첫 트랜잭션이 행을 바꿀 때까지 두 번째 트랜잭션은 기다리고, 잠금 획득 뒤 갱신된 상태를 읽어 중복 완료를 거부한다.

### 4. 일정 정책 분리

`MedicineSchedulePolicy.shouldTakeOn(group, date)`는 날짜 계산만 담당한다. service의 현재 날짜 조회와 분리했기 때문에 고정된 날짜로 다음을 테스트할 수 있다.

- 시작일 전에는 모든 유형 false
- DAILY는 시작일부터 매일 true
- WEEKLY는 쉼표 단위의 정확한 요일만 true
- INTERVAL은 시작일을 고정 기준점으로 사용

### 5. JPA orphan removal

보호자는 `User.guardians` 컬렉션의 구성원이다. repository에서 guardian만 삭제하고 부모 컬렉션에 남겨 두면 같은 영속성 context가 다시 관계를 유지할 수 있다.

```text
User.removeGuardian(guardian)
  -> 컬렉션에서 제거
  -> orphanRemoval=true
  -> flush 시 DELETE guardian
```

이는 DB의 `ON DELETE CASCADE`와 다르다. 애플리케이션의 영속성 context가 연관관계 변화를 감지해 DELETE를 만든다.

## 선택한 방식과 대안

### 선택: 작은 전용 명령 서비스

조회 CRUD와 상태 변경을 구분하고 `IntakeCommandService`만 완료 상태를 바꾸게 했다. Android 전용 추상화나 범용 command bus는 만들지 않았다.

### 선택: 기존 성공 응답 유지

React가 사용하는 URL, request JSON, 성공 status 200, 한국어 문자열 응답은 유지했다. 이번 Goal은 실패 계약을 명확히 하지만 성공 API의 형태를 재설계하지 않는다.

### 검토 후 제외: Telegram SDK adapter 전체 재설계

알림 정책과 전달 adapter 분리는 Goal 7 범위다. 이번에는 상태 변경만 Telegram handler 밖으로 이동했다.

### 검토 후 제외: DB UNIQUE 제약 추가

중복 약 이름의 동시 생성은 DB 제약 없이는 완전히 막을 수 있다. 새 제약은 migration과 기존 데이터 검토가 필요하므로 관련 없는 V2를 만들지 않고 후속 후보로 남긴다.

## 변경된 파일과 역할

| 영역 | 핵심 파일 | 역할 |
|---|---|---|
| 오류 | `ApiError`, `GlobalExceptionHandler` | JSON 오류와 400·401·403·404 변환 |
| 입력 | `UserCreateRequest`, `ReceiverCreateRequest`, `MedicineRequest` | 필드 검증 |
| 교차 검증 | `ValidMedicineSchedule`, `MedicineScheduleValidator` | 일정 유형별 값 규칙 |
| 명령 | `IntakeCommandService`, `IntakeCompletion` | REST·Telegram 공통 완료 |
| 일정 | `MedicineSchedulePolicy` | DAILY·WEEKLY·INTERVAL 판정 |
| 동시성 | `IntakeLogRepository` | 완료 대상 행의 비관적 잠금 |
| 보호자 | `User`, `UserService` | orphan removal 기반 실제 삭제 |
| 테스트 | `ApiIntegrationTests` | 핵심 API 계약 |
| 테스트 | `IntakeCommandServiceIntegrationTest` | Telegram 권한·공통 상태 전이 |
| 테스트 | `MedicineSchedulePolicyTest` | 일정 계산 |
| 테스트 | `FlywayMariaDbIntegrationTest` | 실제 MariaDB 타입·FK·상태 update |

## 데이터 및 설정 변경

- Flyway migration 추가 없음
- 도메인 테이블 구조 변경 없음
- 환경변수 추가·변경 없음
- `guardian` 관계에 JPA `orphanRemoval=true` 추가: 스키마가 아니라 삭제 동작 변경
- 8081과 운영 데이터는 구현·로컬 테스트 단계에서 변경하지 않음

## 시행착오와 문제 해결 과정

첫 신규 테스트 실행은 21건 중 1건 실패했다. 보호자 삭제 API가 200을 반환했지만 `guardianRepository.existsById()`는 true였다. SQL 로그에도 DELETE가 없었다.

원인은 repository 직접 삭제와 `User.guardians` 컬렉션 상태가 불일치한 것이었다. 부모 컬렉션 제거와 `orphanRemoval`로 수정하자 DELETE가 발생했고 전체 테스트가 성공했다. 자세한 재현과 판단은 [API·상태 전이 트러블슈팅](../troubleshooting/api-validation-and-state-transition.md)에 기록한다.

## 로컬 테스트와 보호 시나리오

`./gradlew clean test bootJar --no-daemon` 결과:

| 항목 | 결과 |
|---|---|
| 전체 | 22건 |
| 실패·오류 | 0건 |
| skip | 1건: 로컬 MariaDB 환경변수 부재 시 실제 DB 테스트 |
| bootJar | 성공 |

보호 시나리오:

- 인증 없는 API 401 JSON
- 일반 사용자의 관리자 삭제 403과 데이터 유지
- 빈 이름·잘못된 interval·음수 path 400
- 미존재 자원 404
- 타 사용자 guardian 삭제 403과 행 유지
- React WEEKLY payload 등록, INTERVAL 수정, 약 중단
- PENDING 최초 완료 성공, TAKEN 중복·MISSED 완료 거부
- 잘못된 Telegram chat ID의 복용 완료 거부
- 실제 MariaDB의 enum, BIT, TIME, DATETIME, FK, 조건부 상태 update

## 배포 및 검증

feature CI와 8081 비파괴 인수 테스트 후 갱신한다. 8081 테스트는 기존 데이터를 생성·수정·삭제하지 않는 인증·오류·조회 계약 중심으로 수행한다. 실제 메시지는 발송하지 않으며 enhancement profile의 Telegram·scheduler false를 유지한다.

## 운영 시 주의 사항

- `PESSIMISTIC_WRITE` 조회는 반드시 짧은 transaction 안에서 사용한다.
- Telegram handler에서 repository 상태 변경을 다시 추가하지 않는다.
- 새 요청 DTO에는 controller의 `@Valid`가 함께 있어야 annotation이 실행된다.
- 새 예외를 만들면 `ApiError` 형식과 HTTP 의미를 유지한다.
- guardian 삭제는 repository 직접 delete보다 부모 컬렉션 관계 변경을 우선한다.

## 직접 확인하는 방법

```powershell
.\gradlew.bat clean test bootJar --no-daemon
```

테스트 보고서에서 `ApiIntegrationTests`, `IntakeCommandServiceIntegrationTest`, `MedicineSchedulePolicyTest`를 확인한다. CI에서는 `FlywayMariaDbIntegrationTest`의 `skipped=0` 확인 단계도 성공해야 한다.

## 알려진 한계

- 약 이름 중복은 repository 사전 조회 기반이라 동시 insert를 DB 제약으로 막지 않는다.
- 성공 응답이 문자열인 기존 API 형태는 호환성을 위해 유지했다.
- Telegram 오류 callback UX와 메시지 재시도는 Goal 7 범위다.
- 운영 8080은 아직 이 feature를 사용하지 않는다.

## 후속 Goal에 넘길 내용

- Goal 6: `fieldErrors`를 React form에 표시할지 검토
- Goal 7: Telegram 오류 callback 응답, 알림 정책, 재시도·멱등성 확장
- 후속 DB 후보: 약 이름의 사용자별 활성 UNIQUE 전략과 기존 데이터 검토

관련 기록: [API·상태 전이 트러블슈팅](../troubleshooting/api-validation-and-state-transition.md)
