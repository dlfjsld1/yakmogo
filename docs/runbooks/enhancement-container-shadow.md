# Yakmogo enhancement Docker shadow 런북 (전환 전 기록)

> 현재 상태: 2026-07-15에 Docker 8081 전환을 완료해 18081 shadow는 더 이상 실행하지 않는다. 현재 운영 절차는 [8081 container 배포 런북](enhancement-container-deployment.md)을 따른다.

## 안전 경계

이 런북은 host 18081에서 shadow container를 검증하기 위한 것이다. `yakmogo.service`, `yakmogo-enhancement.service`, 기존 Docker Compose project와 MariaDB 설정을 변경하지 않는다.

## 산출물

```text
yakmogo-enhancement-<backend-sha>-linux-arm64.tar
yakmogo-enhancement-<backend-sha>-linux-arm64.tar.sha256
release-manifest.txt
```

manifest image 필드:

```text
IMAGE_REF=yakmogo-enhancement:<backend-sha>
IMAGE_ARCH=arm64
IMAGE_TAR=yakmogo-enhancement-<backend-sha>-linux-arm64.tar
IMAGE_TAR_SHA256=<sha256>
```

## 서버 경로

| 역할 | 경로 |
|---|---|
| Compose | `/home/pi/docker/yakmogo-enhancement/compose.yml` |
| 공개 release 설정 | `/home/pi/docker/yakmogo-enhancement/.release.env` |
| 비밀 환경파일 | `/etc/yakmogo/yakmogo-enhancement.env` |
| runner staging | `/var/lib/yakmogo-container-deploy/incoming/<sha>/` |
| root work | `/var/lib/yakmogo-container-deploy/work/` |
| 결과 | `/var/lib/yakmogo-container-deploy/state/deployment-result.txt` |
| helper | `/usr/local/sbin/yakmogo-enhancement-container-shadow` |

`.release.env`에는 image ref, 18081, frontend URL, 예상 JS bundle만 있고 비밀번호·token은 없다. 비밀값은 기존 root:root 600 환경파일에만 남는다.

## 최초 설치

검증된 feature 소스를 Pi 임시 staging으로 옮긴 뒤 root로 다음 installer를 실행한다.

```bash
deploy/cicd/install-enhancement-container-shadow.sh <repository-root>
```

installer는 Compose file, root helper, 정확한 helper 한 개만 허용하는 sudoers와 권한 분리 directory를 만든다.

## request 형식

```text
BACKEND_SHA=<40 lowercase hex>
MODE=shadow
```

helper는 인자를 받지 않으며 request가 `shadow`가 아니면 거부한다.

## 직접 확인

```bash
docker inspect yakmogo-enhancement --format '{{.Config.User}} {{.HostConfig.NetworkMode}} {{.HostConfig.ReadonlyRootfs}}'
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18081/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18081/api/v1/users
systemctl show yakmogo yakmogo-enhancement -p MainPID
docker ps --format '{{.Names}} {{.ID}} {{.Status}}'
```

기대값:

```text
10001:10001 host true
18081 / -> 200
18081 protected API -> 401
8080과 8081 PID 불변
기존 container ID 불변
```

전체 `docker inspect` 환경값이나 `/etc/yakmogo/yakmogo-enhancement.env` 내용은 출력하지 않는다.

## shadow 제거

cutover 승인 전 shadow를 제거해야 할 때는 root가 고정 Compose 경로에서 `docker compose down`을 실행한다. image와 staging 자동 삭제는 Goal 9 보관 정책이 정해진 뒤 활성화한다.

## cutover 전 확인 목록

- shadow가 최소 한 번 clean start 성공
- Flyway validation 성공
- bot·scheduler false 로그 또는 설정 확인
- 8080·systemd 8081·기존 containers 불변
- 이전 systemd JAR checksum 기록
- container 배포 실패 시 systemd 8081 복원 명령 준비
- Uptime Kuma의 8081 monitor 변경은 Goal 9 승인 전 금지

## 검증된 현재 값

```text
shadow URL: http://<PI_LAN_IP>:18081
image: yakmogo-enhancement:9e1deebc7a3f6c2e9ee70a8042d171a7d4072c5d
image ID: sha256:7a6b733c70cc6eecedfc529942914fab42990ea6a9bcf5eab0a6d4aad3b4cde4
image tar SHA-256: 5291a042d935e877757aadee3e9e514fa185d82fd82520bf6991be339cb2308e
state: SHADOW_READY
```

cutover 승인 전에는 다음 명령을 실행하지 않는다.

```text
systemctl stop yakmogo-enhancement
container의 SERVER_PORT=8081 전환
기존 enhancement workflow의 container deploy helper 변경
```
