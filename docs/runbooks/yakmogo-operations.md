# Yakmogo 관측·백업·복원 운영 런북

이 문서는 Yakmogo 앱과 전용 MariaDB만 다룬다. Transmission, Jellyfin, Samba 등 홈서버의 다른 서비스와 Pi 전체 복구 절차는 포함하지 않는다.

## 구성과 데이터 경계

- 앱 container: `yakmogo-app`
- DB container: `yakmogo-mariadb`
- DB volume: `${COMPOSE_PROJECT_NAME}-mariadb-data`
- 설치 설정: portable 폴더의 `.env`, 권한 `600`
- 정기 dump: portable 폴더의 `backups/scheduled`
- update 직전 dump: portable 폴더의 `backups/update`
- 공개 포트: 앱 HTTP 포트만 공개하며 MariaDB 3306은 Docker 내부 network에서만 사용한다.

Container 재생성과 DB 복구는 다른 작업이다. Container는 image로 다시 만들 수 있지만 DB volume이 손상되거나 삭제되면 SQL dump가 필요하다. 따라서 복구 기준은 volume 복사가 아니라 checksum을 검증할 수 있는 논리 dump다.

## 새 서버 설치

새 서버에 필요한 선행 프로그램은 Docker Engine과 Docker Compose plugin뿐이다. release package를 푼 설치 사용자가 다음 한 명령을 실행한다.

```bash
./setup.sh
```

첫 실행에서 접속 URL과 관리자 비밀번호를 확인한다. 나머지 DB 비밀번호와 인증 secret은 자동 생성한다. `setup.sh`는 저수준 `install.sh`로 앱과 DB를 설치한 뒤 `sudo`를 한 번 사용해 backup timer를 등록하고 `/actuator/health`까지 확인한다.

설치가 자동으로 건드리는 호스트 범위는 `/etc/systemd/system/yakmogo-backup.service`, `/etc/systemd/system/yakmogo-backup.timer` 두 파일뿐이다. Java, host MariaDB, runner, Tailscale과 다른 Compose project는 설치하지 않는다.

## Health endpoint

```text
GET /actuator/health
```

정상이면 HTTP 200과 `{"status":"UP"}`만 반환한다. DB 연결을 포함한 전체 상태는 반영하지만 component 이름과 상세 오류는 숨긴다. `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump`는 Actuator endpoint로 노출하지 않는다.

```bash
curl -i http://127.0.0.1:8081/actuator/health
curl -i http://127.0.0.1:8081/api/v1/users
docker compose --env-file .env -f compose.yml ps
```

정상 조건은 health HTTP 200, 보호 API HTTP 401, 앱·DB container `running`, DB health `healthy`다.

## 정기 백업 정책

- 실행 시각: 매월 1일 03:35, `Asia/Seoul`
- 보관 개수: 백업 폴더별 최신 3개
- 삭제 조건: 새 dump 생성, gzip 검사, SHA-256 재검증이 모두 성공한 뒤 dump가 3개를 초과한 경우
- 실패 조건: dump 또는 검증이 실패하면 임시 파일만 제거하고 기존 dump는 삭제하지 않는다.
- 중복 실행: `.backup.lock`의 배타 잠금으로 차단한다.
- missed timer: Pi가 예약 시간에 꺼져 있었다면 `Persistent=true`에 따라 다음 부팅 후 한 번 실행한다.

백업 파일과 `.sha256`은 한 쌍이다. 한쪽만 이동하거나 이름을 바꾸지 않는다.

## Timer 설치와 제거

`setup.sh`를 사용하지 않고 timer만 나중에 추가할 때 다음 명령을 사용한다. 이 명령은 `/etc/systemd/system`을 변경하므로 승인 전에는 실행하지 않는다.

```bash
cd /home/<user>/yakmogo-portable
sudo ./install-backup-timer.sh <user>
systemctl list-timers yakmogo-backup.timer --no-pager
systemctl cat yakmogo-backup.service yakmogo-backup.timer
```

즉시 시험할 때는 정기 시각을 바꾸지 않고 service만 한 번 실행한다.

```bash
sudo systemctl start yakmogo-backup.service
sudo systemctl status yakmogo-backup.service --no-pager
journalctl -u yakmogo-backup.service -n 100 --no-pager
```

Timer만 제거하고 기존 dump는 유지한다.

```bash
sudo ./remove-backup-timer.sh
```

## 복원 리허설

운영 DB나 현재 enhancement volume에 덮어쓰지 않는다. 별도 Compose project와 빈 임시 volume을 만든 뒤 복원한다.

1. dump와 checksum을 같은 폴더에 둔다.
2. `sha256sum --check <dump>.sha256`을 실행한다.
3. 현재 `.env`를 복사하되 project 이름, HTTP 포트와 DB 비밀번호를 시험용으로 바꾼다.
4. 시험용 MariaDB만 기동한다.
5. 빈 DB임을 확인한 뒤 `restore.sh`로 복원한다.
6. Flyway 이력과 핵심 table row 수를 원본과 비교한다.
7. 시험용 Compose project와 volume만 제거한다.

자동 통합검증은 `scripts/ci/test-portable-install.sh`가 이 흐름을 수행한다. 실제 Pi 리허설에서도 운영 `yakmogo`, 운영 8080과 현재 enhancement volume을 대상으로 `down -v`를 실행하지 않는다.

## Uptime Kuma 계획

Kuma 1.23.17은 monitor의 `description` 필드를 지원한다. Yakmogo monitor는 사용자가 실제 이용하는 운영 endpoint 하나만 둔다.

| 이름 | 대상 | 주기 | 정상 조건 |
|---|---|---:|---|
| `Yakmogo` | `http://<PI_LAN_IP>:8080/` | 60초 | HTTP 200 |

현재 운영 8080은 Actuator가 없는 이전 release이므로 루트 화면을 감시한다. 8081 enhancement는 release candidate 검증용이므로 Kuma에 추가하지 않는다. Goal 10에서 새 release가 main과 8080에 승격되면 기존 monitor 하나의 URL만 `/actuator/health`로 바꿔 이력과 알림 설정을 유지한다.

설명에는 다음 내용을 넣는다.

```text
감시 대상: 환경, 포트와 실행 방식
정상 조건: 기대 HTTP 상태와 body
장애 영향: 사용자가 겪는 증상
확인 명령: curl, docker compose ps 또는 systemctl status
복구 문서: docs/runbooks/yakmogo-operations.md
```

기존 `Yakmogo` monitor에 설명만 추가해 재사용한다. 8081 enhancement, MariaDB container와 GitHub Actions self-hosted runner에는 별도 Kuma monitor를 만들지 않는다. 운영 `/actuator/health`가 앱과 DB 연결을 함께 판단하므로 최종적으로도 Yakmogo monitor 하나면 충분하다.

## 장애 확인 순서

1. `curl`로 해당 포트의 health 또는 루트 HTTP 상태를 확인한다.
2. `docker compose ... ps`로 앱과 DB 상태를 확인한다.
3. `docker compose ... logs --tail 100 yakmogo-app`과 MariaDB log를 확인한다.
4. `df -h`와 `docker system df`로 디스크 여유를 확인한다.
5. update 직후 장애면 `update.sh` 출력과 `backups/update`의 dump를 확인한다.
6. 앱 image 문제면 직전 image로 되돌리고, DB migration 문제는 image rollback과 분리해 판단한다.
7. DB 복원이 필요하면 checksum이 맞는 dump를 별도 빈 volume에서 먼저 검증한다.

## 민감정보

- `.env`, SQL dump와 checksum은 Git에 commit하지 않는다.
- `.env`, dump와 backup directory는 설치 사용자만 읽을 수 있게 권한 `600` 또는 `700`을 유지한다.
- `docker inspect`의 environment 전체, dump 본문, Telegram token과 관리자 비밀번호를 로그나 문서에 붙이지 않는다.
- Uptime Kuma 설명에는 비밀번호, token, Chat ID를 기록하지 않는다.
