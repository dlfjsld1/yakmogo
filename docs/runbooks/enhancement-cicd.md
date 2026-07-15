# 8081 CI/CD 배포 런북 (Goal 8 JAR 방식 기록)

> 현재 상태: 이 문서는 Goal 8에서 검증한 JAR/systemd 배포 방식의 기록이다. 2026-07-15에 8081 runtime을 Docker로 전환했으며 현재 절차는 [8081 container 배포 런북](enhancement-container-deployment.md)을 따른다. container 자동 배포는 runner sudoers 승인·설치와 `enhancement` 병합 전이라 아직 활성화하지 않았다.

## 목적과 안전 경계

이 런북은 backend `enhancement`와 web `enhancement`를 통합해 Raspberry Pi의 고도화 8081에만 배포하는 절차다.

절대 자동화하지 않는 대상:

- Git `main`
- `yakmogo.service`
- 운영 8080 JAR과 경로
- 운영 DB `yakmogo`
- release tag push

## 구성 요소

| 구성 요소 | 책임 |
|---|---|
| Backend CI | backend test, MariaDB migration test, bootJar |
| Web checkout | lint, test, production build |
| `build-release-candidate.sh` | 통합 JAR과 checksum 생성 |
| `verify-integrated-jar.sh` | SPA와 enhancement profile 계약 검사 |
| GitHub artifact | 7일 동안 JAR·checksum·manifest 보관 |
| Pi self-hosted runner | GitHub artifact를 outbound로 받아 staging에 배치 |
| root deploy helper | backup, 8081 교체·재시작·health·rollback |

## 승인 전 준비된 명령

로컬 통합 후보:

```bash
cd yakmogo-web
npm ci
npm run lint
npm test
npm run build

cd ../yakmogo-backend
scripts/ci/build-release-candidate.sh ../yakmogo-web/dist
```

산출물:

```text
build/release/yakmogo-enhancement-<sha>.jar
build/release/yakmogo-enhancement-<sha>.jar.sha256
build/release/release-manifest.txt
```

## 최초 1회 서버 설치

1. 전용 `yakmogo-runner` 사용자 생성
2. 공식 Linux ARM64 GitHub Actions runner를 `/var/lib/yakmogo-runner`에 설치
3. repository runner로 등록하고 `yakmogo-enhancement` 전용 label 부여
4. runner를 전용 systemd service로 설치하되 애플리케이션 환경파일 읽기 권한은 부여하지 않음
5. `/var/lib/yakmogo-deploy/incoming`을 runner 쓰기, root 읽기 구조로 생성
6. `/usr/local/sbin/yakmogo-enhancement-deploy`를 root:root 755로 설치
7. 정확한 helper 경로만 허용하는 sudoers 파일을 root:root 440으로 설치

현재 설치값:

```text
runner user: yakmogo-runner
runner home: /var/lib/yakmogo-runner
runner name: yakmogo-pi-enhancement
runner service: actions.runner.dlfjsld1-yakmogo.yakmogo-pi-enhancement.service
runner labels: self-hosted, Linux, ARM64, yakmogo-enhancement
deploy environment: enhancement branch only
```

일반 shell 전체와 임의 `systemctl`, 임의 파일 경로를 sudoers에 허용하지 않는다. runner 등록 token은 설치 순간에만 사용하고 파일·workflow secret·문서에 저장하지 않는다.

## 네트워크 흐름

```text
GitHub-hosted runner --HTTPS--> GitHub artifact
Raspberry Pi runner --outbound HTTPS--> GitHub Actions
Raspberry Pi runner --local sudo fixed helper--> 8081 files/systemd
```

공유기 port forwarding, public SSH, GitHub-hosted runner에서 LAN으로 들어오는 연결은 만들지 않는다.

## 배포 입력 검증

root helper가 허용할 입력은 다음 형식으로 제한한다.

- backend SHA: 40자리 lowercase hexadecimal
- web SHA: 40자리 lowercase hexadecimal
- checksum: 64자리 lowercase hexadecimal
- JS bundle: `/assets/index-[A-Za-z0-9_-]+\.js`
- candidate: 고정 staging root 아래 `yakmogo-enhancement-<backend-sha>.jar`

request·manifest·candidate·checksum은 일반 파일만 허용하고 symlink를 거부한다. candidate와 checksum을 root 전용 임시 디렉터리로 복사한 뒤 배포 직전 다시 hash한다.

## backup 순서

```text
/var/backups/yakmogo/enhancement/<timestamp>-<backend-sha>/
  jar/yakmogo-0.0.7-SNAPSHOT.jar
  systemd/yakmogo-enhancement.service
  runtime/start.sh
  secrets/yakmogo-enhancement.env
  release-manifest.txt
  deployment-result.txt
```

권한 계획:

- backup directory: root:root 700
- environment backup: root:root 600
- manifest: 비밀값 없이 checksum·SHA·timestamp·결과만 기록

환경 파일은 `cp --preserve=mode,ownership,timestamps`로 root 내부에서 복사하며 `cat`, `env`, shell trace로 출력하지 않는다.

## checksum 검증

CI:

```bash
sha256sum yakmogo-enhancement-<sha>.jar > yakmogo-enhancement-<sha>.jar.sha256
```

원격 staging:

```bash
sha256sum yakmogo-enhancement-<sha>.jar
```

두 값을 64자리 hex로 비교한다. checksum 파일이 가리키는 경로를 그대로 신뢰하지 않고 helper가 고정 candidate 파일을 직접 hash한다.

## 정상 배포 상태 전이

```text
STAGED
  -> VERIFIED
  -> BACKED_UP
  -> 8081_STOPPED
  -> JAR_REPLACED
  -> 8081_STARTED
  -> READY
  -> ACCEPTED
```

각 상태와 timestamp를 manifest와 journal에 남긴다. token, password, SSH key, 환경파일 내용은 남기지 않는다.

## readiness 절차

최대 60회, 1초 간격으로 확인한다.

```bash
systemctl is-active yakmogo-enhancement
curl --fail --silent http://127.0.0.1:8081/
curl --fail --silent http://127.0.0.1:8081/assets/<expected>.js
curl --silent --output /dev/null --write-out '%{http_code}' \
  http://127.0.0.1:8081/api/v1/users
```

마지막 API는 `401`이어야 한다. `200`이면 보호 경계가 깨진 것이므로 성공으로 처리하지 않는다.

## rollback 상태 전이

```text
FAILED_AFTER_REPLACEMENT
  -> 8081_STOPPED
  -> JAR_RESTORED
  -> UNIT_AND_CONFIG_RESTORED
  -> DAEMON_RELOADED
  -> 8081_STARTED
  -> PREVIOUS_HEALTH_CONFIRMED
  -> ROLLED_BACK
```

후보 교체 전 실패는 현재 서비스를 중지하지 않는다. rollback 자체가 실패하면 반복 재시작하지 않고 8081을 중지한 상태로 명확한 실패를 반환하며, 운영 8080은 건드리지 않는다.

## 의도적 실패 검증 계획

실제 rollback 시험은 승인 후 다음처럼 수행한다.

1. 현재 정상 8081 JAR checksum과 HTTP 상태 기록
2. 정상 release candidate 배포 성공 확인
3. JAR 내용은 정상이나 expected JS bundle을 존재하지 않는 값으로 제한한 test invocation 실행
4. 새 JAR 교체 후 health contract 실패 확인
5. 자동 rollback 실행 확인
6. 이전 JAR checksum 복원 확인
7. 8081 `/` 200, 보호 API 401 확인
8. 8080 PID·JAR checksum·HTTP 200 불변 확인
9. 정상 enhancement artifact로 최종 상태 재확인

실패 검증을 위해 애플리케이션 코드를 깨뜨리거나 운영 DB를 변경하지 않는다.

## 긴급 수동 확인

```bash
systemctl is-active yakmogo yakmogo-enhancement
systemctl show yakmogo yakmogo-enhancement -p MainPID -p NRestarts
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/
journalctl -u yakmogo-enhancement --since=-10min --no-pager
```

전체 process environment, systemd Environment, 환경파일 내용을 출력하지 않는다.

배포 결과는 가장 최근 backup의 `deployment-result.txt`와 `release-manifest.txt`에서 확인한다. 이 두 파일에는 token과 환경변수 값이 없으며 root만 읽을 수 있다.

## DB migration 주의

애플리케이션 시작 시 Flyway가 고도화 DB를 migration할 수 있다. 자동 JAR rollback은 이미 적용된 migration을 취소하지 않는다.

- migration은 backward-compatible하게 작성한다.
- 적용 전 중복·용량·lock 위험을 검사한다.
- DB backup과 restore rehearsal은 Goal 9 정책을 따른다.
- 운영 DB에는 이 workflow가 연결되지 않는다.
