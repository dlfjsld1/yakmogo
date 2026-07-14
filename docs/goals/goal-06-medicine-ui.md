# Goal 6: React 약 관리 UI·UX

## 한 문장 요약

약 등록·수정·삭제를 하나의 접근 가능한 화면 흐름으로 완성하고, 요청 상태·오류·인증 만료·모바일 동작을 자동 테스트와 실제 8081 브라우저 검증으로 보호했다.

## 작업 배경

기존 `MedicineManage` 화면은 약 등록과 삭제는 가능했지만 수정 UI가 없었다. API 호출 실패는 대부분 `alert` 또는 콘솔 출력에 머물렀고, 저장 버튼을 연속으로 누르면 같은 요청이 여러 번 전송될 수 있었다. 로딩 실패와 인증 만료도 사용자가 화면에서 원인을 이해하고 다시 행동하기 어려웠다.

Goal 5에서 백엔드의 `PUT /api/v1/medicine-groups/medicines/{groupId}` 계약과 일정 검증이 마련됐으므로 Goal 6에서는 새 API를 만들지 않고 이 계약을 그대로 사용했다. 백엔드 Java 코드는 변경하지 않았고 문서만 별도 `feature/medicine-ui` 브랜치에 기록했다.

## 변경 전 문제

- 등록 폼은 있었지만 기존 약의 이름·주기·시작일·시간을 편집할 진입점이 없었다.
- `DAILY`, `WEEKLY`, `INTERVAL` 값 변환이 등록 함수 내부에만 들어 있어 수정에 재사용할 수 없었다.
- 등록·수신자 추가 요청 중 버튼이 활성 상태라 중복 제출 가능성이 있었다.
- 조회 오류는 `console.error`, 저장 오류는 `alert`로 처리돼 화면 상태와 분리돼 있었다.
- 인증 만료 인터셉터가 `alert` 뒤 즉시 이동해 사용자가 로그인 화면에서 이동 이유를 다시 확인할 수 없었다.
- 삭제가 `window.confirm`에 의존해 보조 기술과 자동화 브라우저에서 일관되게 다루기 어려웠다.
- 웹 저장소에 테스트 실행 명령과 컴포넌트 테스트 환경이 없었다.

## 이번 Goal 범위

- 기존 PUT API를 사용한 약 수정 UI
- 세 일정 유형의 등록·기존 값 편집·요청 payload 변환
- 약·수신자 저장 및 삭제 중 중복 요청 방지
- 로딩, 빈 목록, 네트워크 오류, 401, 403, 성공 피드백의 화면 표시
- 인증 만료 사유의 일회성 로그인 화면 전달
- 약 삭제의 화면 내 2단계 확인
- 모바일 레이아웃과 접근 가능한 이름·역할 보강
- Vitest, jsdom, React Testing Library 기반 핵심 화면 테스트
- 통합 JAR을 이용한 8081 실제 브라우저 인수 테스트

## 범위에서 제외한 내용

- 백엔드 API와 도메인 코드 변경
- 운영 `main`, 운영 8080, 운영 DB `yakmogo` 변경
- 8081 자동 배포 파이프라인과 자동 롤백 구현(Goal 8 범위)
- Playwright 테스트 파일 도입: 현재 규모에서는 컴포넌트 테스트와 이번 실제 브라우저 인수 절차로 충분하다고 판단했다.
- 전체 웹 화면의 `alert` 제거: 약 관리와 인증 만료 경로만 다뤘다.
- npm 감사에서 발견한 기존 의존성의 일괄 업그레이드
- Android, Device, Pairing, FCM 코드·API·테이블

## 도입한 기술과 개념

### Vitest와 jsdom

Vitest는 Vite의 TypeScript·JSX 변환 체계를 공유하는 테스트 실행기다. `jsdom`은 실제 Chrome을 띄우지 않고 Node.js 안에 DOM과 `localStorage`, `sessionStorage` 같은 브라우저 API를 제공한다. 빠른 컴포넌트 상태 검증에 적합하지만 CSS 픽셀 배치와 네이티브 브라우저 대화상자까지 완전히 재현하지는 않는다. 그래서 마지막 단계는 실제 8081 브라우저로 보완했다.

`vitest.config.ts`는 다음 경계를 둔다.

```text
environment: jsdom
setupFiles: src/test/setup.ts
restoreMocks: true
```

`setup.ts`는 `jest-dom`의 읽기 쉬운 DOM matcher를 등록하고 각 테스트가 끝날 때 `cleanup()`을 호출한다. 정리를 하지 않으면 앞 테스트의 DOM이 남아 같은 문구가 여러 개 검색되는 거짓 실패가 생긴다.

### React Testing Library

테스트는 내부 state나 CSS class보다 사용자가 접근하는 역할과 이름을 조회한다.

```text
getByRole('button', { name: '혈압약 삭제' })
getByLabelText('복용 간격 (일)')
findByRole('alert')
```

이 방식은 테스트 가능성과 접근성을 같은 계약으로 만든다. 버튼의 시각적 텍스트가 짧은 `삭제`여도 `aria-label`에 약 이름을 포함하므로 여러 카드에서 대상을 명확히 구분할 수 있다.

### 한 폼으로 등록과 수정 공유

`MedicineForm` 상태와 `editingMedicineId`를 사용한다.

```text
editingMedicineId = null  -> POST 등록
editingMedicineId = id    -> PUT 수정
```

수정 버튼을 누르면 목록 응답의 `scheduleValue`, `startDate`, `intakeTime`을 폼 상태로 역변환한다. 저장할 때는 같은 함수가 다시 백엔드 계약으로 직렬화한다. 클래스를 추가하거나 범용 폼 프레임워크를 도입하지 않고 현재 한 화면 안에서만 공유했다.

### 일정 유형 변환

| 화면 유형 | 화면 상태 | API `scheduleValue` |
|---|---|---|
| `DAILY` | 추가 입력 없음 | `null` |
| `WEEKLY` | 선택 요일 배열 | `Monday,Wednesday` 같은 쉼표 문자열 |
| `INTERVAL` | 1 이상의 숫자 문자열 | `5` 같은 문자열 |

수정 시에는 반대로 WEEKLY 문자열을 쉼표로 나누고 INTERVAL 문자열을 숫자 입력값으로 채운다. 유형을 바꾸면 이전 유형의 요일·간격 상태를 초기화해 잘못된 값이 새 요청에 섞이지 않게 했다.

### 중복 제출 방지

저장 함수 시작 시 이미 요청 중이면 즉시 반환하고, 요청이 끝날 때까지 제출·취소 버튼을 비활성화한다.

```text
idle -> saving=true -> POST/PUT -> 재조회 -> saving=false
```

약 삭제와 수신자 저장·삭제도 각각 진행 중 ID 또는 boolean을 둬 같은 종류의 요청이 겹치지 않게 했다. 테스트에서는 완료되지 않은 Promise를 사용해 첫 POST가 대기 중일 때 두 번째 클릭이 요청 수를 늘리지 않는지 확인한다.

### 화면 피드백과 인증 만료

약 관리 화면은 성공 메시지를 `role="status"`, 오류를 `role="alert"`로 렌더링한다. Axios 오류는 다음 순서로 사용자 문구를 정한다.

1. 401: 인증 만료 안내
2. 403: 권한 부족 안내
3. 응답 자체 없음: 네트워크 안내
4. 백엔드 `message`가 있으면 해당 검증 문구
5. 그 외 작업별 기본 문구

401 인터셉터는 토큰을 제거하고 이동하기 전에 `sessionStorage`의 `yakmogo-auth-notice`에 사유를 저장한다. 로그인 화면은 최초 렌더에서 값을 읽고 즉시 제거한다. 따라서 새로고침 뒤 오래된 경고가 계속 남지 않는 일회성 전달이다. 인증 로직은 특정 Android 클라이언트 모델을 추가하지 않고 현재 웹의 표시 책임만 개선했다.

### 삭제 전략

등록·수정 API는 변경된 엔티티가 아니라 문자열만 반환하므로 성공 후 서버 목록을 재조회한다. 삭제는 성공한 ID를 알고 있어 functional state update로 제거한다.

```text
POST/PUT 성공 -> fetchData() -> 서버가 확정한 전체 목록
DELETE 성공   -> setMedicines(current => current.filter(...))
```

삭제 첫 클릭은 서버를 호출하지 않고 같은 카드에 `취소`와 `삭제 확인`을 표시한다. 두 번째 확인에서만 DELETE가 전송된다. 이는 브라우저 네이티브 confirm 의존성을 없애면서 실수 방지 단계는 유지한다.

## 선택한 방식과 대안

### 선택: 현재 화면 안의 명시적 상태

폼·피드백·요청 상태가 한 화면에만 있으므로 별도 상태 관리 라이브러리나 범용 toast 시스템을 도입하지 않았다. 컴포넌트 크기는 커졌지만 데이터 흐름을 한 파일에서 추적할 수 있고 추가 추상화 비용이 없다.

### 선택: 수정 후 재조회, 삭제 후 로컬 제거

PUT 응답에 갱신 엔티티가 없으므로 응답을 추측해 로컬 객체를 조립하지 않았다. 삭제는 대상 ID가 확실하므로 네트워크 왕복을 하나 줄이는 functional update를 사용했다.

### 검토 후 제외: UI 편의를 위한 백엔드 응답 변경

Goal 5에서 검증된 API 계약을 UI 때문에 바꾸면 Goal 범위와 회귀 위험이 커진다. 현재 응답만으로 요구 흐름이 가능하므로 제외했다.

### 검토 후 제외: 범용 toast·form 라이브러리

현재 필요한 것은 한 화면의 소수 상태다. 라이브러리를 도입하면 번들·학습·테스트 범위가 늘어나므로 `Feedback` 상태와 기본 HTML form을 사용했다.

### 검토 후 제외: Playwright를 저장소 CI에 즉시 추가

인증 proof와 8081 환경을 CI에 안전하게 구성하는 작업은 현재 Goal보다 크다. 핵심 분기는 jsdom 테스트로 보호하고, 실제 통합 동작은 임시 proof를 사용한 수동 자동화 인수 절차로 검증했다. 지속 가능한 E2E 구성은 CI/CD 및 테스트 환경 자격증명 설계와 함께 후속 후보로 남긴다.

## 실제 동작 흐름

### 화면 진입

```text
MedicineManage mount
  -> GET /api/v1/users/{userId}
  -> GET /api/v1/medicine-groups/users/{userId}/medicines
  -> 둘 다 성공: 사용자·약 목록 표시
  -> 하나라도 실패: 전체 오류 화면 + 다시 시도
```

두 조회는 `Promise.all`로 함께 실행한다. 사용자와 약 중 한쪽만 오래된 상태로 보이는 부분 성공 화면을 만들지 않는다.

### 등록·수정

```text
약 추가 또는 수정 클릭
  -> 공통 폼 열기
  -> 일정 유형별 입력과 클라이언트 기본 검증
  -> saving=true, 버튼 잠금
  -> POST 또는 PUT
  -> 목록 재조회
  -> 모달 닫기 + status 성공 문구
```

### 삭제

```text
삭제 클릭
  -> 카드 안에서 삭제 확인/취소 표시 (API 호출 없음)
  -> 삭제 확인 클릭
  -> DELETE /api/v1/medicine-groups/medicines/{id}
  -> 성공 ID를 목록에서 functional update로 제거
  -> 빈 상태 또는 나머지 목록 + status 표시
```

### 인증 만료

```text
보호 API 401
  -> 관리자·magic token 제거
  -> sessionStorage에 만료 이유 1회 저장
  -> 로그인 경로로 이동
  -> App이 이유를 alert 영역에 표시하고 저장값 제거
```

## 변경된 파일과 역할

### web 저장소

| 파일 | 역할 |
|---|---|
| `src/pages/MedicineManage.tsx` | 등록·수정 공통 폼, 요청 상태, 피드백, 2단계 삭제, 모바일·접근성 UI |
| `src/pages/MedicineManage.test.tsx` | 로딩·조회 오류·401·등록 중복 방지·수정·삭제 테스트 |
| `src/App.tsx` | 인증 만료 사유를 로그인 화면에 일회성 표시 |
| `src/App.test.tsx` | 인증 만료 문구 소비 검증 |
| `src/api/http.ts` | 401 사유 저장과 이동, 전역 alert 제거 |
| `src/test/setup.ts` | jest-dom 등록과 테스트별 DOM cleanup |
| `vitest.config.ts` | jsdom 테스트 환경 설정 |
| `package.json`, `package-lock.json` | test script와 Vitest/RTL 개발 의존성 |
| `tsconfig.node.json` | Vitest 설정 파일 타입 검사 포함 |
| `README.md` | lint/test/build 실행 방법 |

### backend 저장소

| 파일 | 역할 |
|---|---|
| `docs/goals/goal-06-medicine-ui.md` | Goal 6 설계·검증·배포 기록 |
| `docs/troubleshooting/ui-testing-and-deployment.md` | 실제 실패와 재발 방지 절차 |
| Goal·troubleshooting README | 문서 탐색 링크와 상태 갱신 |

백엔드 Java, 설정, DB migration 파일은 변경하지 않았다.

## 데이터 및 설정 변경

- DB 스키마·Flyway migration 변경 없음
- 환경변수와 systemd unit 변경 없음
- `yakmogo_enhancement`에 E2E 사용자 ID 3과 4를 순차적으로 만들었고 각 검증 뒤 삭제함
- 최종 cleanup 후 8081 관리자 API 기준 enhancement 사용자 수 `0`
- 임시 약도 사용자 삭제 전에 화면에서 삭제했고 관련 데이터가 남지 않음
- 로그인 proof는 5분 TTL로 생성해 로컬·원격 임시 파일에서만 사용하고 모두 삭제함
- Telegram bot과 scheduler는 계속 비활성으로 실제 메시지 발송 없음
- 운영 DB `yakmogo` 데이터 변경 없음
- npm 개발 의존성: Vitest, jsdom, React Testing Library, jest-dom, user-event 추가

## 시행착오와 문제 해결 과정

세부 명령과 원인 구분은 [UI 테스트와 8081 배포 트러블슈팅](../troubleshooting/ui-testing-and-deployment.md)에 기록했다.

1. PowerShell 실행 정책이 `npm.ps1`을 차단해 `npm.cmd`를 사용했다.
2. 샌드박스가 esbuild의 상위 경로 조회를 막아 승인된 검사 환경에서 테스트를 재실행했다.
3. 첫 테스트 설정에 cleanup 훅이 없어 이전 DOM이 누적됐다. 명시적 `afterEach(cleanup)`으로 해결했다.
4. 긴 SSH 인라인 배포 명령이 Windows·원격 셸 인용을 거치며 깨졌다. 비밀 없는 파일형 스크립트로 전환했다.
5. PowerShell 파이프 인코딩 때문에 sudo 비밀번호 바이트가 달라졌다. ASCII 파일 stdin으로 전달해 해결했다.
6. 실제 브라우저에서 `window.confirm`이 자동화 제어를 막았다. 화면 내 2단계 삭제로 변경하고 새 테스트와 재배포로 검증했다.
7. 감사에서 기존 의존성 취약점 16건을 발견했지만 범용 버전 업그레이드는 범위 밖이라 후속 위험으로 기록했다.

## 테스트와 보호 시나리오

최종 로컬 결과:

| 검사 | 결과 |
|---|---|
| `npm test` | 2개 파일, 7개 테스트 성공 |
| `npm run lint` | 성공 |
| `npm run build` | 성공, 95 modules transformed |
| `git diff --check` | 성공 |

자동 테스트가 보호하는 시나리오:

- 최초 로딩 status와 빈 약 목록
- 조회 실패 alert와 다시 시도
- 401 인증 만료 문구
- WEEKLY 등록 payload와 요청 중 버튼 잠금·POST 1회
- 기존 INTERVAL 값 편집과 PUT 후 재조회
- 삭제 첫 클릭에는 API 미호출, 확인 뒤 DELETE 및 목록 제거
- 로그인 화면에서 인증 만료 사유 1회 표시·sessionStorage 제거

실제 8081 브라우저 결과:

- 단기 Telegram proof 로그인 성공
- WEEKLY `월·수` 약 등록 성공
- 기존 WEEKLY 값이 수정 폼에서 pressed 상태로 복원됨
- INTERVAL `5일`로 수정 성공, 목록 문구 `5일 간격` 확인
- 최종 빌드에서 DAILY 등록 후 2단계 삭제 성공
- 빈 상태·성공 status 확인
- 390×844 viewport에서 `documentWidth=390`, 가로 넘침 없음
- 약 추가·수신자 추가 버튼 모두 표시
- 최종 브라우저 console error/warning 0건
- 로드 bundle `assets/index-CdQ-TAxj.js`

## 배포 및 검증

최종 통합 JAR은 web `dist`를 백엔드 bootJar의 `BOOT-INF/classes/static`에 오버레이했다. 소스의 기존 정적 파일과 백엔드 Git 변경은 만들지 않았다.

| 항목 | 결과 |
|---|---|
| 최종 JAR SHA-256 | `2f6e3bf4e590005f87cada33b9208ae232c31f8260721831eef32f19901216c1` |
| 최종 8081 백업 | `/home/pi/myprojects/yakmogo-enhancement/backup-goal6-20260714-153500` |
| `yakmogo-enhancement.service` | active |
| 8081 `/` | HTTP 200 |
| 8081 무인증 `/api/v1/users` | HTTP 401 |
| 운영 `yakmogo.service` | active |
| 운영 8080 `/` | HTTP 200 |

배포는 후보 JAR 체크섬 확인, 현재 JAR 타임스탬프 백업, 8081 서비스만 재시작, 최대 60초 번들·HTTP polling 순서로 수행했다. 실패 시 백업 JAR을 되돌리고 8081만 다시 시작하도록 보호했다. 자동 배포를 활성화한 것은 아니며 이 수동 절차의 자동화는 Goal 8 범위다.

## 운영 시 주의 사항

- 웹만 빌드한 `dist`를 별도 서버로 배포하지 않는다. 백엔드와 통합한 JAR이 필요하다.
- 등록·수정 뒤 목록 재조회가 실패하면 저장 자체는 성공했을 가능성이 있으므로 다시 시도 전에 목록을 확인한다.
- `sessionStorage` 인증 문구에는 토큰이나 서버 원문을 저장하지 않는다.
- 테스트에서 HTTP 모듈을 mock했으므로 API 계약 변경 시 백엔드 통합 테스트와 실제 8081 인수 검증도 함께 갱신한다.
- npm 취약점은 감사 결과만 기록된 상태다. 무작정 `npm audit fix --force`를 실행하지 말고 직접 의존성별 릴리스 노트와 회귀 테스트를 거쳐야 한다.

## 직접 확인하는 방법

웹 저장소에서:

```bash
npm ci
npm run lint
npm test
npm run build
```

8081에서:

1. 개발용 관리자 또는 유효한 Telegram 링크로 로그인한다.
2. 약 추가에서 DAILY를 저장한다.
3. 수정에서 WEEKLY 요일을 저장하고 다시 열어 선택 복원을 확인한다.
4. INTERVAL 5일로 바꾸고 목록 문구를 확인한다.
5. 삭제를 한 번 눌러 API 호출 전 확인 UI가 나타나는지 본다.
6. 삭제 확인을 눌러 성공 문구와 빈 상태를 확인한다.
7. 저장 버튼을 빠르게 여러 번 눌러도 한 요청만 진행되는지 disabled 상태를 본다.

운영 8080에서 이 절차를 수행하지 않는다.

## 알려진 한계

- `MedicineManage.tsx`가 약과 수신자 관리를 함께 담당해 파일이 크다. 현재 한 화면의 상태가 강하게 연결돼 있어 분리하지 않았지만 향후 독립 변경이 잦아지면 작은 표시 컴포넌트 분리를 검토할 수 있다.
- 수신자 삭제는 아직 `window.confirm`을 사용한다. 실제 브라우저에서 확인된 문제는 약 삭제에서 해결했으며 수신자 전체 UX 고도화는 별도 범위다.
- CI의 컴포넌트 테스트는 jsdom이므로 실제 CSS 레이아웃을 보장하지 않는다.
- CI에 지속형 실제 브라우저 E2E는 아직 없다.
- `npm audit` 기준 16건(높음 9, 보통 6, 낮음 1)이 남아 있다. 직접 의존성 Axios 1.13.2, React Router DOM 7.10.0, Vite 7.2.6도 포함된다.
- audit 권고에는 브라우저에서 사용하지 않는 Node adapter나 SSR 경로 관련 항목도 섞여 있지만, 영향이 없다고 단정하지 않고 별도 의존성 보안 Goal 후보로 남긴다.

## 후속 Goal에 넘길 내용

- Goal 7: 알림 정책·Telegram 전달 신뢰성. 이번 UI나 인증 표시를 함께 변경하지 않는다.
- Goal 8: web dist와 backend bootJar의 공식 통합 패키징, 60초 health polling, 체크섬, 롤백 자동화.
- 후속 보안 작업: Axios·React Router·Vite와 전이 의존성의 안전한 버전 업그레이드 및 회귀 검증.
- 후속 UI 작업 후보: 수신자 삭제의 화면 내 확인, 저장 후 재조회 부분 실패 안내, 유지 가능한 브라우저 E2E 자격증명 설계.

관련 기록: [UI 테스트와 8081 배포 트러블슈팅](../troubleshooting/ui-testing-and-deployment.md)
