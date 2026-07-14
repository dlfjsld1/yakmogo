# Goal 8: CI/CD와 8081 자동 배포·롤백

## 현재 상태

**완료.** 통합 JAR·manifest 생성, ARM64 self-hosted deploy job, 고정 경로 root helper, 자동 rollback을 활성화했다. 정상 8081 자동 배포와 교체 후 readiness 실패를 이용한 실제 rollback probe가 성공했고, 두 과정에서 운영 8080 PID·JAR·HTTP 상태가 변하지 않았음을 확인했다.

Goal 7은 backend `enhancement`에 merge commit `7dc6ea1`로 병합했고 [통합 CI run 29314937366](https://github.com/dlfjsld1/yakmogo/actions/runs/29314937366)이 성공한 뒤 Goal 8을 시작했다.

## 한 문장 요약

backend와 web을 재현 가능하게 함께 빌드하고 SPA 누락·오래된 asset·8081 안전 profile을 검사해 SHA-256이 포함된 release candidate를 만드는 단계까지 자동화했으며, 외부 상태를 바꾸는 배포 단계는 사용자 승인 전 비활성 상태다.

## 작업 배경

Goal 1~7의 8081 배포는 다음 절차를 수동으로 반복했다.

1. web `npm run build`
2. backend `bootJar`
3. web `dist`를 JAR의 `BOOT-INF/classes/static`에 overlay
4. 후보 checksum 계산
5. Raspberry Pi에 전송
6. 기존 JAR 백업
7. 8081만 재시작
8. 최대 60초 HTTP polling
9. 실패 시 기존 JAR 복원

이 절차는 실제로 작동했지만 사람이 오래된 web bundle을 넣거나, overlay 과정에서 과거 hash asset을 JAR에 남기거나, systemd `active`만 보고 너무 일찍 성공 처리할 수 있다. Goal 8은 먼저 빌드 산출물의 계약을 자동 검사하고, 승인 뒤에만 원격 변경 단계를 연결한다.

## 구현 범위

- 기존 backend test·bootJar CI 유지
- feature CI에서 release tooling shell 문법과 정상·실패 fixture 실행
- `-PwebDistDir` Gradle 입력으로 web `dist`를 정식 resource source로 사용
- 기존 SPA index·asset을 제외해 오래된 hash asset이 남지 않도록 처리
- 통합 JAR의 index·전체 web 파일·asset 집합·8081 profile 검증
- SHA-256과 release candidate 생성
- backend `enhancement` push에서만 실행될 release-candidate workflow 작성
- workflow 동시 실행 직렬화
- artifact 7일 보관
- backend SHA·web SHA·JAR 이름·SHA-256·예상 JavaScript bundle을 담은 manifest 생성
- ARM64 전용 self-hosted deploy job
- 인자를 받지 않는 8081 전용 root deploy helper와 정확한 한 명령만 허용하는 sudoers
- JAR·unit·start script·환경파일 backup과 실제 실패 후 rollback 검증 mode
- CI/CD 설계와 배포 런북 문서화

## 제외 범위

- 운영 8080, 운영 DB, main 변경
- GitHub에 SSH·sudo·DB·Telegram secret 저장
- Android 코드·API·테이블

## 통합 JAR 생성 방식

### 변경 전 overlay 방식

기존 수동 방식은 완성된 backend JAR에 `jar uf`로 web 파일을 덮었다. 새 index는 들어가지만 기존 `assets/index-OLD.js`가 남는다. 브라우저가 새 index만 사용하면 당장 보이지 않지만 JAR이 실제 web 산출물과 일치하지 않고 크기와 조사 대상이 계속 늘어난다.

### 변경 후 Gradle resource 입력

```text
web npm build
  -> yakmogo-web/dist
  -> ./gradlew clean test bootJar -PwebDistDir=<dist>
     -> 기존 static/index.html, static/assets/**, static/vite.svg 제외
     -> 새 dist를 static/에 포함
     -> backend images는 유지
```

`webDistDir`를 주지 않은 일반 backend CI는 기존 resource를 사용하므로 feature backend 검증 방식은 바뀌지 않는다. release candidate 생성 때만 외부 web 산출물을 입력한다.

## 산출물 검증 계약

`verify-integrated-jar.sh`는 다음 조건을 모두 확인한다.

- web `dist/index.html`과 JAR의 index가 byte 단위로 동일
- web `dist`의 모든 파일이 JAR에 존재
- JAR의 `static/assets` 파일 집합이 web `dist/assets`와 정확히 동일
- index가 JavaScript bundle을 참조
- `application-enhancement.yml`의 port가 8081
- datasource가 `yakmogo_enhancement`
- Telegram bot과 scheduler 비활성 설정 존재

fixture 테스트는 세 경우를 실행한다.

| fixture | 기대 결과 |
|---|---|
| index와 asset이 모두 일치 | 성공 |
| JAR에 과거 `old.js`가 남음 | 실패 |
| index가 참조하는 `app.js` 누락 | 실패 |

## 현재 workflow

`.github/workflows/release-candidate.yml`은 `enhancement` push만 수신한다. feature와 PR에서는 self-hosted runner를 사용하지 않는다.

```text
backend enhancement push
  -> backend checkout (해당 merge SHA)
  -> web enhancement checkout
  -> Java 21 / Node 22
  -> web lint + test + build
  -> backend test + MariaDB migration test + bootJar
  -> integrated JAR contract 검증
  -> SHA-256와 release manifest 생성
  -> GitHub artifact 7일 보관
  -> ARM64 + yakmogo-enhancement label runner가 artifact 다운로드
  -> 고정 staging에 배치하고 root helper 호출
  -> 8081 전용 backup·배포·readiness·실패 rollback
```

`concurrency.group=enhancement-release`, `cancel-in-progress=false`로 두 enhancement release가 동시에 실행되지 않게 했다. 앞 release를 취소하면 배포 중 상태가 불명확해질 수 있으므로 나중 실행이 기다리는 정책이다.

## 로컬 검증 결과

실제 Goal 6 web `dist`와 Goal 7 backend를 사용했다.

- `clean test bootJar`: 성공
- web lint: 성공
- web Vitest: 2 files, 7 tests 성공
- web production build: 성공
- 통합 JAR 검증: 성공
- 오래된 asset fixture: 의도한 실패
- 누락 asset fixture: 의도한 실패
- 최종 로컬 통합 JAR SHA-256: `976ebe14fa96e65833e691a309dcb8d74ddf1bbd7f02ba870dee6a1b2b2f6efc`
- release manifest의 backend SHA·web SHA·JAR SHA·JS bundle 검증: 성공
- 서버 전송: 하지 않음
- 서비스 재시작: 하지 않음

feature CI:

- 커밋: `c23f43c`
- [Backend CI run 29315810864](https://github.com/dlfjsld1/yakmogo/actions/runs/29315810864) 성공
- Java 21 backend test·bootJar 성공
- MariaDB migration test 실제 실행 확인 성공
- release tooling shell 문법과 정상·stale·missing fixture 성공

checksum은 CI에서 만든 한 artifact와 원격으로 전송된 바로 그 파일이 동일한지 확인하는 용도다. 별도 시점에 다시 빌드한 JAR이 같은 hash일 것이라고 가정하지 않는다. 승인 후 배포 판단에는 CI artifact의 checksum을 사용한다.

## 승인 후 구현할 전체 흐름

상세 명령과 파일 책임은 [8081 CI/CD 배포 런북](../runbooks/enhancement-cicd.md)에 기록했다.

```text
검증·통합 JAR·checksum
  -> GitHub artifact 보관
  -> Pi self-hosted deploy job이 outbound 연결로 artifact 다운로드
  -> Pi에서 SHA-256 재검사
  -> root-owned deploy helper 호출
  -> JAR·unit·start script·env 백업
  -> 후보 profile과 고정 경로 재검사
  -> yakmogo-enhancement.service만 중지/시작
  -> 최대 60초 readiness
  -> / 200 + 예상 JS 200 + 보호 API 401
  -> 성공 기록 보존
  -> 실패하면 기존 파일 복원 후 8081 health 재검증
```

## credential과 CI secret

Raspberry Pi는 읽기 전용 확인 결과 `aarch64`, 64-bit Debian 13, systemd 257이다. GitHub 공식 문서는 Linux ARM64 self-hosted runner와 `runs-on: [self-hosted, linux, ARM64]` label 사용을 지원한다.

- <https://docs.github.com/en/actions/reference/runners/self-hosted-runners>
- <https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/use-in-a-workflow>

따라서 권장안은 inbound SSH가 아니라 Pi의 self-hosted runner가 GitHub에 outbound 연결하는 방식이다.

```text
GitHub-hosted assemble job
  -> artifact upload
Pi self-hosted deploy job
  -> artifact download
  -> local checksum
  -> narrow sudo deploy helper
```

이 방식에서는 workflow용 `ENHANCEMENT_SSH_*` secret을 만들지 않는다. runner 등록 token은 GitHub가 설치 시 발급하는 짧은 수명의 1회용 값이며 workflow secret이나 저장소 파일로 보존하지 않는다.

예정된 배포용 GitHub secret: **없음**.

`SUDO_PASSWORD`, DB 비밀번호, Telegram token, 관리자 비밀번호도 GitHub secret으로 추가하지 않는다. root helper는 허용된 고정 명령만 password 없이 실행하도록 좁은 sudoers rule을 사용한다. 애플리케이션 비밀값은 기존 `/etc/yakmogo/yakmogo-enhancement.env`에만 남는다.

검토 후 제외한 대안은 `ENHANCEMENT_SSH_HOST`, `ENHANCEMENT_SSH_PORT`, `ENHANCEMENT_SSH_USER`, `ENHANCEMENT_SSH_PRIVATE_KEY`, `ENHANCEMENT_SSH_KNOWN_HOSTS`를 사용하는 hosted-runner SSH 방식이다. 현재 주소가 `192.168.50.192` 사설망이므로 GitHub-hosted runner가 직접 접근할 수 없고, 이를 위해 SSH port를 인터넷에 공개하거나 별도 VPN credential을 추가하는 것은 현재 규모에서 불필요한 공격면을 만든다.

## 배포 대상과 경로

| 구분 | 계획 경로 |
|---|---|
| self-hosted runner 작업공간 | `/var/lib/yakmogo-runner/_work/` |
| deploy staging | `/var/lib/yakmogo-deploy/incoming/<backend-sha>/` |
| 실행 JAR | `/home/pi/myprojects/yakmogo-enhancement/yakmogo-0.0.7-SNAPSHOT.jar` |
| systemd unit | `/etc/systemd/system/yakmogo-enhancement.service` |
| start script | `/home/pi/myprojects/yakmogo-enhancement/start.sh` |
| 비밀 환경파일 | `/etc/yakmogo/yakmogo-enhancement.env` |
| 백업 | `/var/backups/yakmogo/enhancement/<timestamp>-<backend-sha>/` |
| deploy helper | `/usr/local/sbin/yakmogo-enhancement-deploy` |

후보 artifact에는 backend SHA, web SHA, JAR basename, SHA-256, 예상 JS bundle 경로를 `release-manifest.txt`로 기록한다.

## health check 조건

다음 조건을 모두 만족해야 성공이다.

1. Pi가 다운로드한 후보 SHA-256이 CI checksum과 동일
2. 후보 JAR profile이 8081·`yakmogo_enhancement`·bot false·scheduler false
3. `yakmogo-enhancement.service`가 active
4. 최대 60초 안에 `GET /`가 200
5. 새 index가 참조하는 예상 JS bundle이 200
6. `GET /api/v1/users`가 인증 없이 401
7. 운영 `yakmogo.service`의 PID와 JAR checksum이 배포 전후 동일
8. 8080 `/`가 계속 200

## rollback 조건

다음 중 하나면 자동 rollback 대상으로 설계한다.

- 업로드 checksum 불일치
- 후보 profile 경계 불일치
- 8081 service start 실패
- 60초 readiness timeout
- index 또는 예상 JS bundle 불일치·비정상 HTTP
- 보호 API가 401이 아님
- 운영 8080 PID·checksum·HTTP 불변 조건 위반

후보 교체 전 실패는 실행 파일을 건드리지 않고 종료한다. 교체 후 실패는 백업 JAR·unit·start script·environment file을 원래 권한으로 복원하고 `daemon-reload`, 8081 시작, 이전 health 확인 순서로 처리한다.

Flyway migration은 JAR rollback으로 되돌리지 않는다. migration은 별도 DB 변경이며 자동 역방향 SQL은 데이터 손실 위험이 있다. 따라서 새 migration이 포함된 release는 적용 전 backup과 forward/backward compatibility를 별도로 검증해야 한다.

## 8080과 main이 영향을 받지 않는 근거

- workflow trigger는 backend `enhancement`뿐이다.
- workflow 권한은 `contents: read`다.
- target service 이름을 `yakmogo-enhancement.service`로 고정한다.
- target JAR과 backup 경로에 `yakmogo-enhancement` 또는 `/var/backups/yakmogo/enhancement`만 허용한다.
- deploy helper는 전달받은 임의 service/path를 실행하지 않는다.
- 배포 전후 운영 PID·JAR checksum·HTTP 200을 비교한다.
- main push·tag·release trigger를 추가하지 않는다.
- 운영 DB URL을 workflow나 deploy helper에 전달하지 않는다.

## 활성화 및 실제 검증 결과

2026-07-14 사용자 승인 뒤 다음 구성을 활성화했다.

- runner: `yakmogo-pi-enhancement`, Linux ARM64, 전용 `yakmogo-enhancement` label
- runner systemd: `actions.runner.dlfjsld1-yakmogo.yakmogo-pi-enhancement.service`
- runner version: 공식 `2.335.0`, Linux ARM64 archive SHA-256 검증 성공
- GitHub environment: `enhancement`, 배포 branch pattern은 `enhancement` 하나
- GitHub 배포 secret: 없음
- runner의 `/etc/yakmogo/yakmogo-enhancement.env` 읽기: 차단 확인
- sudo 허용: `/usr/local/sbin/yakmogo-enhancement-deploy` 한 명령만 확인

최초 정상 자동 배포:

- enhancement merge: `5ba8bf206b916a0b9616302d7150bb7bc7139691`
- backend CI: [run 29319118256](https://github.com/dlfjsld1/yakmogo/actions/runs/29319118256)
- assemble + deploy: [run 29319118695](https://github.com/dlfjsld1/yakmogo/actions/runs/29319118695)
- web SHA: `3330335bbdb9cf4e87a2d4873df884c252207624`
- 배포 JAR SHA-256: `7c24e6753f5662d3854d93e6ec49cdbc30b69cb4a80047741e13505a748d14fc`
- 예상 bundle: `/assets/index-CdQ-TAxj.js`
- 8081 `/`: 200, 보호 API: 401
- Telegram bot·scheduler: 기존 enhancement 안전 설정대로 비활성

실제 rollback probe:

- 정상 후보 끝에 무해한 marker를 추가해 실행 JAR checksum 변화
- 존재하지 않는 JS bundle을 readiness 조건으로 주어 교체 후 60초 실패 유도
- `ROLLBACK_STATE=STARTED`와 `ROLLBACK_STATE=CONFIRMED` 확인
- 복원된 enhancement JAR SHA-256: `7c24e6753f5662d3854d93e6ec49cdbc30b69cb4a80047741e13505a748d14fc`
- 운영 JAR SHA-256: `b7670b8089e887181f78592e88dbf7bb12f45427685dfa3deb3a6d73bcdc79ce` 불변
- 운영 PID: `1794958` 불변
- 복원 후 8080 `/`: 200, 8081 `/`: 200, 8081 보호 API: 401
- rollback 결과: `ROLLBACK_PROBE_CONFIRMED`

후보 검증 중 발견한 `pipefail`과 Windows CRLF 문제는 JAR이나 서비스를 교체하기 전에 실패해 운영 상태에 영향이 없었다. 원인과 수정은 [CI/CD 트러블슈팅](../troubleshooting/ci-cd.md)에 기록했다.

## 알려진 위험과 승인 시 결정할 내용

- self-hosted runner는 저장소 workflow 명령을 Pi에서 실행하므로 `deploy` job만 전용 label을 사용하고 feature·PR 검사는 계속 GitHub-hosted runner에서 실행해야 한다.
- public repository에서 self-hosted runner를 사용하는 위험을 줄이기 위해 deploy workflow는 `enhancement` push와 repository-owned workflow만 사용하고 fork PR job에는 해당 label을 절대 지정하지 않는다.
- runner 전용 사용자·systemd service와 narrow sudoers 설치는 Raspberry Pi 운영 파일 변경이다.
- 자동 rollback이 environment file을 복원할 때 현재 비밀값을 로그로 읽지 않도록 root 내부 copy만 사용해야 한다.
- JAR rollback은 DB migration rollback이 아니다.
- 실제 실패 rollback 검증은 8081만 사용하지만 짧은 서비스 중단이 발생한다.

## 승인 후 수행 순서

1. self-hosted ARM64 runner 방식 승인을 확정한다.
2. runner 전용 사용자와 GitHub Actions runner systemd service를 구성한다.
3. `yakmogo-enhancement` 전용 label과 GitHub environment branch 제한을 구성한다.
4. root-owned deploy helper와 narrow sudoers를 설치한다.
5. workflow에 self-hosted artifact download·local deploy job을 추가한다.
6. 정상 8081 자동 배포를 검증한다.
7. 의도적으로 health가 실패하는 후보로 rollback을 검증한다.
8. 정상 artifact로 8081을 복구한다.
9. credential 비노출, backup, 배포 이력, 8080 불변을 확인한다.
