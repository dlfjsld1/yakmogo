# UI 테스트와 8081 배포 트러블슈팅

## 사례 1: PowerShell에서 `npm.ps1` 실행이 차단됨

### 상황

Vitest와 React Testing Library를 설치하기 위해 `npm install`을 실행했다.

### 증상

```text
npm.ps1 cannot be loaded because running scripts is disabled on this system
```

### 원인 확정

Node나 npm 패키지 문제가 아니라 PowerShell이 PATH에서 `npm.ps1`을 먼저 선택했고 현재 실행 정책이 서명되지 않은 PowerShell 스크립트를 차단했다.

### 해결

같은 npm CLI의 Windows 명령 파일인 `npm.cmd`를 사용했다.

```powershell
npm.cmd install
npm.cmd test
```

실행 정책을 시스템 전체에서 완화하지 않았다.

### 배운 점과 재발 방지

Windows에서 npm 실패 로그가 JavaScript 오류 전에 발생하면 어떤 실행 파일이 선택됐는지 먼저 확인한다. 프로젝트 문서에도 `npm.cmd` 대안을 기록했다.

## 사례 2: Vitest가 설정 파일을 읽기 전에 esbuild 접근 오류로 중단됨

### 상황

테스트 파일을 추가한 뒤 첫 RED 실행을 시도했다.

### 증상

```text
Cannot read directory "../..": Access is denied.
Could not resolve ".../vitest.config.ts"
```

### 원인 확정

테스트 코드 실패가 아니라 Vite/esbuild가 설정을 번들링하면서 샌드박스 허용 범위 밖의 상위 디렉터리를 확인하려다 차단됐다. 같은 명령을 승인된 작업 환경에서 실행하자 Vitest가 시작되고 의도한 UI 테스트 실패가 나타났다.

### 해결

소스 경로나 Vitest 설정을 왜곡하지 않고 검사 명령에 필요한 파일 읽기 권한만 사용했다.

### 배운 점과 재발 방지

도구 시작 실패와 테스트 assertion 실패를 구분한다. RED/GREEN 기록에는 Vitest가 실제 테스트를 실행한 결과만 기능 실패로 센다.

## 사례 3: 테스트 DOM이 누적돼 같은 문구가 여러 개 검색됨

### 상황

여러 `MedicineManage` 테스트를 연속 실행했다.

### 증상

두 번째 테스트부터 `등록된 약이 없어요.`가 여러 개라는 오류가 발생했고 `<body>`에 이전 렌더 컨테이너가 계속 쌓였다.

### 조사와 원인

Vitest globals를 사용하지 않는 구성에서는 React Testing Library의 자동 cleanup 감지가 기대대로 작동하지 않았다. 테스트 사이 mock은 초기화됐지만 DOM은 제거되지 않았다.

### 해결

`src/test/setup.ts`에 명시적 훅을 추가했다.

```ts
afterEach(cleanup)
```

### 배운 점과 재발 방지

mock 초기화와 DOM cleanup은 다른 책임이다. 다중 테스트에서 이전 화면이 보이면 locator를 억지로 좁히기 전에 격리 설정을 확인한다.

## 사례 4: 긴 SSH 인라인 배포 명령의 인용이 깨짐

### 상황

통합 JAR의 체크섬 확인, 백업, 재시작, health polling을 한 번의 Windows SSH 명령에 넣었다.

### 첫 번째 증상

```text
cut: the delimiter must be a single character
```

공백 구분자를 전달하는 인용이 Windows PowerShell, OpenSSH, 원격 bash를 거치며 달라졌다. 이 시점에는 JAR 교체 전이라 서비스 영향이 없었다.

### 두 번째 증상

`cut`을 제거한 뒤 체크섬과 백업·JAR 이동은 성공했지만 다음 오류로 재시작 전에 멈췄다.

```text
unexpected EOF while looking for matching '"'
```

따라서 실행 프로세스는 기존 JAR, 디스크 대상은 새 JAR인 중간 상태였다. 서비스 재시작 전이므로 HTTP는 기존 버전으로 계속 응답했다.

### 해결

비밀값이 없는 배포 절차를 `.sh` 파일로 전송하고 원격에서 실행했다. 스크립트는 다음을 순서대로 수행했다.

1. 후보 JAR SHA-256 확인
2. 현재 JAR을 `backup-goal6-<timestamp>`에 복사
3. 후보를 대상 경로로 이동
4. `yakmogo-enhancement.service`만 재시작
5. 최대 60초 동안 HTTP 200과 예상 JS bundle 확인
6. 실패 시 백업 복원과 8081 재시작

### 배운 점과 재발 방지

여러 셸을 통과하는 긴 명령은 작은 따옴표 하나가 안전 절차 전체를 중단시킨다. Goal 8에서는 저장소의 배포 스크립트를 한 셸에서 실행하고 단계별 로그를 구조화해야 한다.

## 사례 5: PowerShell 파이프가 sudo 비밀번호를 다른 바이트로 전달함

### 상황

SSH 인증은 askpass로 처리하고 sudo 암호 한 줄을 PowerShell 파이프로 원격 스크립트 stdin에 전달했다.

### 증상

SSH 로그인은 성공했지만 sudo가 다음을 반환했다.

```text
sudo: no password was provided
sudo: incorrect password attempt
```

### 원인 확정

같은 문자 값을 ASCII 파일 stdin으로 전달하자 sudo 인증이 즉시 성공했다. 암호 자체가 아니라 PowerShell 파이프의 문자 인코딩이 원격 PAM이 기대한 바이트와 달랐다.

### 해결

작업공간 안의 권한 제한 임시 파일을 `cmd.exe` 입력 리디렉션으로 전달했다. 배포 종료 뒤 askpass, sudo 입력, login proof, E2E 스크립트를 모두 삭제하고 존재하지 않음을 확인했다.

### 배운 점과 재발 방지

인증 실패를 곧바로 비밀번호 오류로 단정하지 않는다. Windows PowerShell에서 네이티브 프로그램 stdin으로 민감 문자열을 보낼 때 인코딩 경계를 확인한다. Goal 8에서는 대화형 sudo에 의존하지 않는 제한된 배포 권한 설계가 필요하다.

## 사례 6: `window.confirm`이 실제 브라우저 삭제 검증을 멈춤

### 상황

실제 8081에서 WEEKLY 등록과 INTERVAL 수정 후 삭제 버튼을 눌렀다.

### 증상

페이지가 네이티브 confirm 대화상자를 띄운 상태에서 자동화 클릭과 대화상자 조회가 시간 초과됐다. API DELETE는 아직 실행되지 않았다.

### 판단

단순히 자동화 우회만 추가하면 실제 사용자에게 남아 있는 접근성·제어 일관성 문제는 해결되지 않는다. 네이티브 confirm은 화면 DOM에 없어서 버튼 이름과 상태를 테스트하기도 어렵다.

### 해결

약 카드 안의 명시적 2단계 확인으로 변경했다.

```text
삭제 -> 취소 | 삭제 확인 -> DELETE
```

첫 클릭에는 API가 호출되지 않는 테스트를 추가했다. 최종 8081에서 DAILY 약을 등록하고 삭제, 삭제 확인을 차례로 눌러 성공 status와 빈 목록을 확인했다.

### 배운 점과 재발 방지

자동화하기 어려운 UI는 사용자에게도 상태가 불투명한 경우가 많다. 브라우저 검증 실패를 테스트 도구 문제로만 보지 않고 UI 계약 개선 가능성을 함께 검토한다.

## 사례 7: `npm audit`에 기존 직접 의존성 취약점이 다수 표시됨

### 상황

테스트 개발 의존성을 추가한 뒤 전체 lockfile을 감사했다.

### 결과

2026-07-14 기준:

```text
total 16
high 9
moderate 6
low 1
critical 0
```

직접 의존성 Axios 1.13.2, React Router DOM 7.10.0, Vite 7.2.6과 전이 의존성이 포함됐다. 일부 advisory는 Node HTTP adapter, SSR, dev server처럼 현재 브라우저 SPA 운영 경로와 다르지만 전부 영향 없음으로 단정할 근거는 마련하지 않았다.

### 하지 않은 작업

`npm audit fix` 또는 `--force`를 실행하지 않았다. Goal 6의 UI 흐름과 관계없는 대규모 버전 변동을 함께 넣으면 회귀 원인과 검증 범위가 불명확해진다.

### 후속 절차

1. 직접 의존성별 수정 버전과 breaking change 확인
2. Axios 인터셉터·React Router SPA fallback·Vite 빌드 회귀 검증
3. `npm audit` 재실행
4. 별도 보안 feature 브랜치와 문서로 통합

## 확인된 최종 근거

- web 테스트: 2 files, 7 tests 성공
- web lint/build 성공
- Web feature CI 29312366717 성공
- Backend 문서 feature CI 29312365268 성공
- 최종 JS bundle: `assets/index-CdQ-TAxj.js`
- 최종 JAR SHA-256: `2f6e3bf4e590005f87cada33b9208ae232c31f8260721831eef32f19901216c1`
- 8081 백업: `/home/pi/myprojects/yakmogo-enhancement/backup-goal6-20260714-153500`
- 8081 active/HTTP 200, 무인증 API 401
- 390×844: 가로 넘침 없음
- 실제 WEEKLY 등록, INTERVAL 수정, DAILY 등록·2단계 삭제 성공
- 최종 브라우저 console error/warning 0건
- E2E cleanup 뒤 `yakmogo_enhancement` 사용자 수 0
- 운영 8080 active/HTTP 200, 재시작·파일·DB 변경 없음

관련 Goal: [Goal 6: React 약 관리 UI·UX](../goals/goal-06-medicine-ui.md)
