# Yakmogo Android 2차 개발 로드맵

## 목표와 원칙

가족이 사용하는 Android 앱에서 복약 일정 확인, 복용 완료와 알림 수신을 안전하게 제공한다. 현재 React 관리자 웹과 Telegram을 없애는 프로젝트가 아니라 세 클라이언트를 필요한 역할에 맞게 공존시키는 프로젝트다.

1차 고도화에서는 Android용 코드와 미사용 table을 미리 만들지 않았다. 이 문서는 구현 약속이 아니라 요구사항을 확인할 순서와 backend 경계를 정한 후속 계획이다.

## 예상 사용자와 핵심 화면

- 복용자: 오늘 복용할 약, 예정·완료·미복용 상태, 복용 완료
- 보호자: 관리하는 가족 선택, 지연·미복용 확인, 알림 수신
- 관리자: 사용자·보호자·약 등록과 수정은 초기에는 기존 React 웹 유지
- 공통 화면: 로그인, 관리 대상 선택, 오늘의 복약, 약 상세, 알림 설정, 연결·동기화 상태

가족 규모에서는 모든 웹 관리 기능을 첫 Android 버전에 복제하지 않는다. 첫 버전은 조회와 복용 완료를 중심으로 하고 관리 기능은 실제 사용 요구가 확인된 뒤 옮긴다.

## 인증 경계

현재 Telegram login proof는 Telegram WebView에서 안전하게 access token을 얻기 위한 방식이다. Android가 chatId를 직접 보내거나 Telegram proof를 흉내 내게 만들면 안 된다.

Android 구현 전에 다음 중 하나를 제품 요구사항으로 선택한다.

1. 관리자 발급 초대 링크와 1회용 등록 코드
2. 가족용 계정 로그인과 refresh token
3. Telegram 계정 연결을 이용한 외부 인증

권장 시작점은 관리자 발급 초대와 앱 등록 후 짧은 access token·회전 가능한 refresh token 조합이다. 가족 서비스 규모에서 범용 OAuth server를 직접 만들 필요는 없지만, 장기 token을 단말의 Android Keystore에 보관하고 서버에서 폐기할 수 있어야 한다.

서비스 계층은 기존 `allowedUserIds`와 관리자 여부를 계속 사용한다. Android가 추가될 때만 실제 인증 방식에 맞는 주체 유형을 추가하고, 사용하지 않는 역할 계층은 만들지 않는다.

## Device와 Pairing 판단

Device table은 앱 설치 사실만으로 필요하지 않다. 다음 요구가 확정될 때 도입한다.

- FCM registration token의 소유자와 만료·폐기 추적
- 한 사용자의 여러 휴대폰 관리
- 분실 단말 강제 로그아웃
- 보호자 초대 또는 특정 복용자와 단말 연결

Pairing은 복용자 계정을 보호자 앱에 연결하는 명시적 승인 흐름이 필요할 때만 도입한다. 단순 로그인과 `allowedUserIds`로 충분하면 Pairing table을 만들지 않는다. 도입한다면 짧은 만료시간, 1회 사용, 재사용 방지와 관리자 취소를 필수 조건으로 둔다.

## FCM과 Telegram 공존

현재 알림 판단은 `MedicationAlertPolicy`에 있고 Telegram 전송은 별도 서비스에 있다. 이 분리는 유지한다. 다만 현재 `NotificationDelivery`는 Guardian과 Telegram을 전제로 하므로 그대로 FCM에 억지로 재사용하지 않는다.

요구사항 확정 후 다음 흐름을 목표로 한다.

```text
Scheduler
  -> MedicationAlertPolicy
  -> 알림 대상 결정
  -> Telegram delivery 또는 FCM delivery
  -> channel별 중복 방지·재시도·최종 결과 기록
```

사용자가 Telegram과 FCM을 모두 켰을 때 두 채널 모두 보낼지, 우선 채널만 보낼지 설정 정책을 먼저 정한다. FCM token은 비밀정보로 취급하고 로그에 남기지 않으며 refresh·invalid token 응답에 따라 폐기한다.

## 동기화와 충돌 정책

- 서버를 최종 진실 원본으로 둔다.
- 조회 응답은 단말에 짧게 cache해 오프라인에서도 마지막 상태를 보여준다.
- 복용 완료 명령에는 logId와 client request ID를 사용해 재전송을 멱등 처리한다.
- 이미 TAKEN 또는 MISSED인 기록을 단말이 덮어쓰지 못하게 현재 `IntakeCommandService` 상태 전이 규칙을 유지한다.
- 네트워크 단절 중 완료 입력은 pending queue에 보관하되 사용자에게 미동기화 상태를 표시한다.
- 서버 시간이 상태 판단 기준이며 단말 시각은 화면 표시와 진단 정보로만 사용한다.
- 충돌 시 자동으로 마지막 쓰기를 덮어쓰지 않고 서버 상태를 표시한 뒤 사용자가 확인하게 한다.

## API 변경 후보

실제 Android Goal에서 contract test와 함께 최소 범위로 결정한다.

- Android 인증·refresh·logout
- 내 관리 대상 목록과 오늘의 복약 일정
- 복용 완료 idempotency key
- 증분 동기화를 위한 `updatedAt` 또는 cursor
- FCM token 등록·교체·삭제
- 알림 채널 설정

현재 API를 Android 이름으로 복제하지 않는다. React·Telegram과 공유할 수 있는 도메인 명령은 공유하고, 모바일 화면에만 필요한 response DTO는 별도로 둔다.

## Kotlin 프로젝트 방향

- Kotlin, Jetpack Compose, Material 3
- UI → ViewModel → use case/repository → local/remote data source
- Retrofit 또는 Ktor client 중 하나만 선택
- Room은 오프라인 cache와 pending command가 실제로 필요할 때 도입
- Coroutines·Flow로 상태 관리
- Android Keystore 기반 token 저장
- unit test, fake repository, API contract test와 최소 UI test

멀티모듈은 첫 버전부터 과하게 나누지 않는다. `app`, 필요할 때만 `core-network`와 `core-database` 정도로 시작하고 기능 수가 늘 때 분리한다.

## 단계별 Goal

### Android Goal A1: 요구사항과 인증 결정

사용자 역할, 로그인·초대, 알림 채널과 오프라인 범위를 확정한다. Device·Pairing 필요 여부를 이 단계에서 결정한다.

### Android Goal A2: Backend contract

선택한 인증과 오늘의 복약 조회·완료 API를 DTO와 contract test로 만든다. 기존 React와 Telegram 회귀 test를 함께 통과시킨다.

### Android Goal A3: 앱 기반과 로그인

Compose project, 환경 분리, network 보안, token 저장과 로그인·로그아웃을 구현한다.

### Android Goal A4: 복약 조회와 오프라인 표시

관리 대상 선택, 오늘 일정, 상태 표시와 마지막 동기화 시각을 구현한다. 서버 장애와 빈 상태를 구분한다.

### Android Goal A5: 복용 완료와 충돌 처리

공통 `IntakeCommandService`를 호출하고 중복 tap, 재전송, 이미 완료·미복용 충돌을 검증한다.

### Android Goal A6: FCM

실제 push 요구가 확정된 경우에만 Device token 수명, 알림 설정, Telegram 공존과 channel별 재시도를 구현한다.

### Android Goal A7: 가족 연결

`allowedUserIds`만으로 부족하다고 확인된 경우에만 Pairing 또는 초대 승인 흐름을 구현한다.

### Android Goal A8: 릴리스 검증

실단말, 네트워크 단절, 재부팅, token 만료, 서버 rollback, 접근성, 개인정보 노출과 Play 배포 설정을 검증한다.

## 공존 전략

- React 웹은 관리자용 사용자·약 관리 화면을 유지한다.
- Telegram callback과 Android REST는 같은 복용 상태 전이 서비스를 사용한다.
- Telegram 알림은 FCM 안정화 전후에도 선택 가능한 채널로 남긴다.
- Android 때문에 기존 API 응답을 조용히 깨지 않고 version 또는 호환 DTO를 사용한다.
- Android 완료 판단은 앱 출시가 아니라 기존 웹·Telegram 회귀 test와 운영 rollback까지 포함한다.

