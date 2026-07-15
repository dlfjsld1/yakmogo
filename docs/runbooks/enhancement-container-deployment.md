# Yakmogo enhancement 8081 container 배포 런북

## 현재 상태

- 운영 8080: 기존 `yakmogo.service`, 변경하지 않음
- 고도화 8081: Docker container `yakmogo-enhancement`
- 이전 고도화 unit: `yakmogo-enhancement.service`, `inactive/disabled`
- 18081 shadow: 전환 완료로 종료
- 자동 배포: 코드와 CI 검증 완료, runner sudoers 승인·설치 전

## 역할과 흐름

GitHub-hosted runner가 backend와 web을 검증하고 ARM64 image tar를 만든다. Pi self-hosted runner는 tar, checksum과 manifest를 고정 staging에 놓는다. 인자를 받지 않는 root helper만 image 검증, Compose 교체, readiness와 rollback을 수행한다.

```text
enhancement push
  -> backend/web test와 통합 JAR
  -> linux/arm64 image tar + SHA-256 + manifest
  -> Pi staging
  -> 고정 root helper
  -> Docker 8081
  -> 실패 시 직전 정상 container 복원
```

runner를 Docker group에 넣지 않는다. 허용 후보 sudo 명령은 다음 한 개뿐이다.

```text
/usr/local/sbin/yakmogo-enhancement-container-deploy
```

## 경로

| 역할 | 경로 |
|---|---|
| Compose | `/home/pi/docker/yakmogo-enhancement/compose.yml` |
| 공개 release 설정 | `/home/pi/docker/yakmogo-enhancement/.release.env` |
| 비밀 환경파일 | `/etc/yakmogo/yakmogo-enhancement.env` |
| runner staging | `/var/lib/yakmogo-container-deploy/incoming/<sha>/` |
| root 작업 공간 | `/var/lib/yakmogo-container-deploy/work/` |
| 마지막 결과 | `/var/lib/yakmogo-container-deploy/state/deployment-result.txt` |
| helper | `/usr/local/sbin/yakmogo-enhancement-container-deploy` |

환경파일과 전체 `docker inspect` Env를 출력하지 않는다. `.release.env`에는 image, port, frontend URL과 예상 bundle만 두고 비밀번호·token은 `/etc/yakmogo`의 root 전용 파일에만 둔다.

## 설치

승인 후 검증된 repository checkout에서 root로 실행한다.

```bash
deploy/cicd/install-enhancement-container-deployer.sh <repository-root>
```

installer는 최종 helper·Compose·한 개의 sudoers를 설치하고 예전 JAR·shadow sudoers를 제거한다. 설치 뒤 반드시 `visudo -c`와 runner 허용 명령을 확인한다.

## request

```text
BACKEND_SHA=<40 lowercase hex>
MODE=deploy
```

helper는 인자를 받지 않고 `MODE=deploy`만 허용한다. image tar SHA-256, architecture, backend/web/JAR label과 manifest를 검증하기 전에는 실행 container를 변경하지 않는다.

## readiness

최대 60초 동안 모두 만족해야 한다.

- container running
- 8081 `/` 200
- manifest의 JavaScript bundle 200
- 보호 API `/api/v1/users` 401
- user `10001:10001`
- host network, read-only root, restart `unless-stopped`
- `CapDrop=ALL`, `no-new-privileges`
- 운영 8080 PID·JAR SHA·HTTP 불변
- Yakmogo 이외 container name/ID 불변

## rollback

일반 배포 실패 시 helper가 직전 `.release.env`와 image로 Compose를 다시 올리고 readiness를 재검증한다. 최초 systemd cutover 실패 시에는 기존 unit을 다시 enable/start하고 8081 HTTP를 확인한 뒤 18081 shadow까지 복원했다.

현재 container에서 systemd로 수동 복귀하는 작업은 자동 배포 실패 rollback이 아니라 별도 운영 변경이다. 실행 전 현재 image·manifest·JAR SHA를 기록하고 8081 중단 시간을 알린 뒤 다음 순서를 사용한다.

```text
1. container Compose down
2. yakmogo-enhancement.service enable/start
3. 8081 / 200, 보호 API 401 확인
4. 실패하면 container release 설정으로 다시 Compose up
```

비밀 환경파일 내용을 화면이나 로그에 출력하지 않는다.

## 직접 확인

```bash
systemctl is-active yakmogo yakmogo-enhancement
systemctl is-enabled yakmogo-enhancement
docker inspect yakmogo-enhancement --format '{{.State.Running}} {{.Config.User}} {{.HostConfig.NetworkMode}} {{.HostConfig.ReadonlyRootfs}}'
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/api/v1/users
docker ps --format '{{.Names}} {{.ID}} {{.Status}}'
```

기대값은 운영 8080 200, systemd enhancement `inactive/disabled`, container running, 8081 200, 보호 API 401이다.

## 현재 검증된 release

```text
image: yakmogo-enhancement:9e1deebc7a3f6c2e9ee70a8042d171a7d4072c5d
image ID: sha256:7a6b733c70cc6eecedfc529942914fab42990ea6a9bcf5eab0a6d4aad3b4cde4
image tar SHA-256: 5291a042d935e877757aadee3e9e514fa185d82fd82520bf6991be339cb2308e
state: CONTAINER_ACTIVE
port: 8081
```

image와 staging 보관 기간, 자동 삭제와 DB 백업은 Goal 9에서 정한다. Pi 전체 복구 문서는 사용자가 별도로 요청하기 전에는 수정하지 않는다.
