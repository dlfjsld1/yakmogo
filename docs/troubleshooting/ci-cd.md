# CI/CD 트러블슈팅

## 사례 1: 로컬 검증만 있고 feature 브랜치 보호가 없었음

### 상황

Goal 1 시작 시 backend와 web 저장소 모두 GitHub Actions workflow가 없었다.

### 최초 접근

개발자가 로컬에서 Gradle과 npm 명령을 실행한 뒤 수동 배포했다.

### 발생한 증상

코드 오류가 아니라 보호 절차의 부재였다.

- feature push 시 자동 검사 없음
- backend와 web 검증 명령이 서로 분리됨
- 로컬 환경 차이로 검사를 건너뛸 수 있음
- 배포 전에 어떤 검사를 통과했는지 원격 기록이 없음

### 조사 과정

두 저장소의 `.github/workflows`와 기존 CI 설정을 검색해 아무 workflow도 없음을 확인했다.

### 원인

프로젝트 초기에는 수동 빌드 중심으로 운영됐고 브랜치 고도화 전략에 맞는 자동 검사가 없었다.

### 해결

커밋 `a9600ba`, `b8d8076`에서 기본 CI를 추가했다.

Backend:

```text
Java 21
./gradlew clean test bootJar --no-daemon
```

Web:

```text
Node.js 22
npm ci
npm run lint
npm test --if-present
npm run build
```

적용 브랜치:

```text
enhancement
feature/**
fix/**
```

Goal 1에서는 자동 배포를 넣지 않았다. 8081 자동 배포와 롤백은 Goal 8 범위로 남겼다.

### 검증

- Goal 1 enhancement Backend CI 성공
- Goal 1 enhancement Web CI 성공
- Goal 2 `feature/auth-hardening` Backend CI 성공
- Goal 2 `feature/auth-hardening` Web CI 성공

### 배운 점

CI의 첫 역할은 배포 자동화가 아니라 모든 feature 브랜치가 같은 최소 품질 검사를 피할 수 없게 만드는 것이다.

### 재발 방지

새 feature 브랜치는 반드시 최신 enhancement에서 분기해 workflow를 처음부터 포함한다.

## 사례 2: 최초 기존 feature push에는 실행 기록이 생성되지 않음

### 상황

Goal 1에서 workflow를 새로 추가한 기존 `feature/quality-hardening`과 이를 병합한 `enhancement`를 한 번에 최초 push했다.

### 최초 접근

두 ref를 push하면 각 브랜치에 대해 CI가 실행될 것으로 예상했다.

### 발생한 증상

GitHub Actions API에서 enhancement 실행은 확인됐지만 `feature/quality-hardening` 실행은 찾을 수 없었다.

### 조사 과정

1. branch query API와 전체 Actions 실행 목록을 모두 확인했다.
2. enhancement 실행 두 건은 성공으로 확인했다.
3. `feature/quality-hardening` 이름의 실행은 전체 목록에도 없었다.
4. 이후 enhancement에서 새로 분기한 `feature/auth-hardening`에서는 Backend와 Web CI가 모두 정상 실행됐다.

### 원인

`로그가 남아 있지 않아 재구성 불가`: GitHub가 최초 다중 ref push에서 기존 feature 실행을 만들지 않은 정확한 내부 이유는 확인하지 못했다. workflow branch pattern 자체는 이후 `feature/auth-hardening` 성공으로 정상임이 입증됐다.

### 해결

Goal 1에서는 로컬 전체 검사와 enhancement CI 성공을 병합 근거로 사용했다. 이후 Goal 2에서 새 feature 브랜치의 실제 CI 실행을 확인했다.

### 검증

- Backend feature CI: <https://github.com/dlfjsld1/yakmogo/actions/runs/29302209037>
- Web feature CI: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29302210279>

### 배운 점

CI 설정 파일이 존재한다는 사실과 특정 브랜치에서 실제 run이 생성됐다는 사실은 별도로 확인해야 한다.

### 재발 방지

각 Goal은 feature 최초 push 뒤 Actions API에서 해당 `head_branch`와 commit SHA의 run을 확인한다.

## 사례 3: 오래된 로컬 `main`에서 enhancement를 분기함

### 상황

Goal 1에서 로컬 `main`을 기준으로 `enhancement`와 feature 브랜치를 만들었다.

### 최초 접근

로컬에 보이는 `origin/main` 추적 ref가 최신 원격 상태라고 판단했다.

### 발생한 증상

원격 브랜치 push와 배포 후 `git ls-remote`로 실제 GitHub main을 확인했을 때 로컬 추적 ref보다 앞선 커밋이 발견됐다.

Backend 원격 main에만 있던 커밋:

```text
f5982fb Add MariaDB initial setup instructions to README
a703352 Update README with execution environment requirements
```

Web 원격 main에도 README 커밋 네 건이 추가되어 있었다.

### 조사 과정

1. `git ls-remote origin refs/heads/main` 결과와 로컬 `origin/main`을 비교했다.
2. `git fetch origin --prune`을 실행했다.
3. `git log enhancement..origin/main`으로 누락 커밋을 확인했다.
4. 변경이 README에만 있음을 diff로 확인했다.
5. `merge-base --is-ancestor origin/main enhancement`가 처음에는 실패함을 확인했다.

### 원인

브랜치 생성 전 fetch를 하지 않아 remote-tracking ref가 최신 원격 상태를 반영하지 못했다.

### 해결

로컬·원격 `main`에는 쓰지 않고 최신 `origin/main`을 enhancement에만 병합했다.

```text
Backend enhancement: 53f60d9
Web enhancement: b8a93e0
```

### 검증

- `merge-base --is-ancestor origin/main enhancement` 성공
- 두 enhancement CI 성공
- `main`을 push 대상에서 제외했음을 push 출력으로 확인

### 배운 점

`origin/main`은 원격 서버 자체가 아니라 마지막 fetch 시점의 로컬 기록이다.

### 재발 방지

새 Goal 시작 절차:

```text
git fetch origin --prune
git switch enhancement
git merge --ff-only origin/enhancement
git merge-base --is-ancestor origin/main enhancement
git switch -c feature/<goal-name>
```

원격 main에 새 변경이 있으면 main을 수정하지 않고 enhancement 통합 정책에 따라 반영 여부를 먼저 판단한다.

## 사례 4: 샌드박스 실패와 프로젝트 실패를 구분해야 했음

### 상황

로컬 CI 명령을 처음 실행할 때 Gradle wrapper와 Vite가 실패했다.

### 최초 접근

일반 샌드박스 권한으로 빌드 도구를 실행했다.

### 발생한 증상

Gradle:

```text
Downloading https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
java.net.SocketException: Permission denied
```

Vite:

```text
Cannot read directory "../..": Access is denied.
Could not resolve vite.config.ts
```

### 조사 과정

1. 컴파일 오류가 아니라 다운로드·파일 접근 단계임을 확인했다.
2. 동일 명령을 허용된 외부 실행 권한으로 다시 실행했다.
3. Gradle 배포 다운로드 후 test가 실행되고 Vite build가 성공함을 확인했다.

### 원인

코드 문제가 아니라 실행 샌드박스의 네트워크와 상위 경로 접근 제한이었다.

### 해결

필요한 Gradle·npm 빌드 명령만 제한적으로 승인해 실행했다.

### 검증

동일 소스에서 backend와 web 빌드 성공, 이후 GitHub Actions에서도 성공했다.

### 배운 점

실패 단계가 다운로드, 도구 설정 로드, 컴파일, 테스트 중 어디인지 먼저 분리해야 불필요한 코드 수정을 피할 수 있다.

### 재발 방지

CI 로그와 로컬 로그를 비교하고, 환경 권한 오류를 애플리케이션 결함으로 기록하지 않는다.

## 근거 CI 실행

- Goal 1 Backend enhancement: <https://github.com/dlfjsld1/yakmogo/actions/runs/29301528249>
- Goal 1 Web enhancement: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29301529443>
- Goal 2 Backend feature: <https://github.com/dlfjsld1/yakmogo/actions/runs/29302209037>
- Goal 2 Web feature: <https://github.com/dlfjsld1/yakmogo-web/actions/runs/29302210279>

## 사례 5: 완성된 JAR overlay가 과거 SPA asset을 남김

### 상황

Goal 6·7의 수동 통합 JAR은 backend `bootJar`를 만든 뒤 `jar uf`로 web `dist`를 덮었다.

### 발생한 증상

JAR 목록에는 새 `index-CdQ-TAxj.js`와 과거 `index-Doi2ps8g.js`가 함께 있었다. 새 index는 올바른 bundle을 참조해 8081은 정상 동작했지만 artifact가 현재 web build와 정확히 일치하지 않았다.

### 원인

ZIP/JAR update는 같은 경로를 교체하지만 새 dist에 없는 과거 파일을 삭제하지 않는다. hash 기반 SPA asset은 build마다 이름이 바뀌므로 단순 overlay만 반복하면 오래된 파일이 누적된다.

### 해결

`-PwebDistDir` Gradle 입력이 있으면 기존 backend resource의 SPA index·assets·vite icon을 제외하고 새 dist를 `static` source로 추가했다. Telegram 이미지처럼 backend가 소유하는 `static/images`는 유지한다.

### 검증

verifier가 JAR의 `static/assets` 집합과 web `dist/assets` 집합을 정렬 비교한다. fixture에 `old.js`를 추가하면 의도대로 실패하고 실제 Goal 6 dist는 통과했다.

### 배운 점

브라우저가 정상이라는 사실만으로 release artifact가 깨끗하다고 볼 수 없다. hash asset은 포함 여부뿐 아니라 집합의 정확한 일치를 검사해야 한다.

## 사례 6: JAR asset directory 항목을 실제 파일로 오인함

### 증상

첫 verifier 정상 fixture가 다음 diff로 실패했다.

```text
@@
+
 app.js
```

### 조사 과정

`jar tf` 출력을 확인하니 `BOOT-INF/classes/static/assets/` directory entry가 있었다. prefix를 먼저 제거한 뒤 `/`로 끝나는 항목을 거르면서 directory가 빈 문자열로 바뀌어 비교 목록에 남았다.

### 해결

원본 JAR entry에서 `/`로 끝나는 directory를 먼저 제거하고 그 다음 asset prefix를 제거했다.

```text
jar entries -> directory 제거 -> prefix 제거 -> sort
```

### 재발 방지

정상, stale, missing 세 fixture가 feature CI에서 매번 실행된다.

## 사례 7: checksum 파일이 CI runner 절대 경로를 기록함

### 상황

첫 로컬 release script는 절대 경로 변수에 바로 `sha256sum`을 실행했다.

### 증상

checksum 파일에 다음처럼 빌드 머신 경로가 포함됐다.

```text
<hash> */c/Users/.../build/release/yakmogo-enhancement-local.jar
```

GitHub runner에서는 `/home/runner/work/...`가 들어가므로 Raspberry Pi에서 `sha256sum -c`로 그대로 재사용할 수 없다.

### 해결

release directory로 이동한 subshell에서 basename에 대해 checksum을 생성한다.

```text
<hash>  yakmogo-enhancement-<sha>.jar
```

원격 helper는 checksum 파일 경로 자체를 신뢰하지 않고 고정 staging 파일의 hash를 직접 다시 계산할 계획이다.

checksum의 목적은 독립된 두 빌드가 동일하다는 보장이 아니라 CI artifact와 원격 전송본이 byte 단위로 같다는 보장이다. 배포 단계에서 JAR을 다시 빌드하지 않고 검증된 artifact를 그대로 전송해야 한다.

## 사례 8: sandbox의 Git Bash 표준 도구 차단

### 증상

첫 로컬 shell test에서 다음 오류가 발생했다.

```text
dirname: command not found
mktemp: command not found
```

### 원인

스크립트 문법이나 PATH 선언 문제가 아니라 sandbox가 Git Bash의 `usr/bin` 표준 도구 실행을 제한했다. 같은 Bash executable을 허용된 작업공간 접근으로 실행하자 `dirname`, `mktemp`, `find`, `jar`, `unzip`이 정상 동작했다.

### 해결과 판단

프로젝트 코드를 바꾸지 않고 동일 테스트를 제한적으로 승인해 다시 실행했다. 이후 정상 fixture 성공과 두 실패 fixture 거부를 확인했다.

### 배운 점

CI shell script 실패도 애플리케이션 실패와 마찬가지로 명령 부재, sandbox 차단, 스크립트 논리를 분리해 봐야 한다.

## 사례 9: 배포 서버에 JDK의 `jar` 도구가 없음

### 증상

Raspberry Pi에는 Java 애플리케이션 실행 환경과 `unzip`은 있지만 JDK 명령인 `jar`는 설치되어 있지 않았다. 초기 root helper는 후보 목록 검사와 rollback probe 생성에 `jar tf`, `jar uf`를 사용했다.

### 판단과 해결

배포 하나를 위해 서버에 JDK 전체를 추가하지 않았다. ZIP 형식인 실행 JAR의 목록은 이미 설치된 `unzip -Z1`로 확인한다. rollback probe는 정상 후보 끝에 무해한 marker bytes를 붙여 checksum만 바꾸고, 존재하지 않는 JS bundle을 readiness 조건으로 사용해 교체 후 실패와 복원을 실제로 통과시킨다.

### 배운 점

CI 머신에 있는 개발 도구가 배포 서버에도 있다고 가정하면 안 된다. 서버 의존성을 먼저 조사하고 이미 있는 표준 도구로 같은 안전 계약을 만족시킬 수 있는지 확인한다.
