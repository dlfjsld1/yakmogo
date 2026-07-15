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
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/api/v1/users
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

운영 DB나 현재 운영 volume에 덮어쓰지 않는다. 별도 Compose project와 빈 임시 volume을 만든 뒤 복원한다.

1. dump와 checksum을 같은 폴더에 둔다.
2. `sha256sum --check <dump>.sha256`을 실행한다.
3. 현재 `.env`를 복사하되 project 이름, HTTP 포트와 DB 비밀번호를 시험용으로 바꾼다.
4. 시험용 MariaDB만 기동한다.
5. 빈 DB임을 확인한 뒤 `restore.sh`로 복원한다.
6. Flyway 이력과 핵심 table row 수를 원본과 비교한다.
7. 시험용 Compose project와 volume만 제거한다.

자동 통합검증은 `scripts/ci/test-portable-install.sh`가 이 흐름을 수행한다. 실제 Pi 리허설에서도 운영 `yakmogo`, 운영 8080과 운영 volume을 대상으로 `down -v`를 실행하지 않는다.

## Uptime Kuma 설정

Kuma 1.23.17은 monitor의 `description` 필드를 지원한다. Yakmogo monitor는 사용자가 실제 이용하는 운영 endpoint 하나만 둔다.

| 이름 | 대상 | 주기 | 정상 조건 |
|---|---|---:|---|
| `Yakmogo Health` | `http://<PI_LAN_IP>:8080/actuator/health` | 60초 | HTTP 200, `{"status":"UP"}` |

2026-07-15 운영 전환 때 기존 monitor 하나의 이름과 URL만 바꿔 이력과 알림 설정을 유지했다. 8081 enhancement, MariaDB container와 GitHub Actions runner에는 별도 monitor를 만들지 않는다. 운영 `/actuator/health`가 앱과 DB 연결을 함께 판단하므로 Yakmogo monitor 하나면 충분하다.

설명에는 다음 내용을 넣는다.

```text
감시 대상: 환경, 포트와 실행 방식
정상 조건: 기대 HTTP 상태와 body
장애 영향: 사용자가 겪는 증상
확인 명령: curl, docker compose ps 또는 systemctl status
복구 문서: docs/runbooks/yakmogo-operations.md
```

2026-07-15 적용 결과는 monitor 하나, 60초, active이며 설명에 목적·대상·정상 조건·확인 명령·이 문서 경로를 기록했다. 변경 전 Kuma DB는 `/var/backups/yakmogo/uptime-kuma-monitor-<시각>/kuma.db`에 권한 `600`으로 보관하고 SHA-256을 검증했다.

## 2026-07-15 운영 전환 기준선

- 설치 위치: `/home/pi/yakmogo-portable`
- Compose project: `yakmogo`
- 서비스: 앱 8080, 전용 MariaDB는 내부 network에서만 3306 사용
- 선택 기능: Telegram bot과 scheduler 활성
- 기존 systemd `yakmogo.service`: 중지·비활성, 파일과 host MariaDB는 초기 rollback을 위해 보존
- 기존 8081 enhancement container: 제거, volume은 초기 rollback을 위해 보존
- 운영 전환 전·직전 dump: `/var/backups/yakmogo/production-transition-<시각>`
- 정기 backup timer: active·enabled, 매월 1일 03:35 KST
- Flyway: V1 baseline과 V2 migration 성공

운영 전환 직후 users 1, guardian 1, medicine_group 2, intake_log 66건이 기존 DB와 같고 중복 복약 일정은 0건이었다. 루트와 health는 HTTP 200, 미인증 보호 API는 HTTP 401, 8081은 미기동 상태를 확인했다. 실제 값은 시점에 따라 변하므로 이후 장애 판단에서는 최신 검증 backup과 현재 row 수를 함께 비교한다.

초기 rollback이 필요하면 먼저 현재 운영 DB를 새 dump로 보존한다. 앱 image 문제는 `update.sh`의 직전 image rollback을 우선 사용한다. 새 Docker DB를 폐기하고 기존 host DB·systemd 서비스로 돌아가는 작업은 전환 이후 입력된 데이터의 유실 위험이 있으므로 자동화하지 않으며, 두 DB의 차이를 확인하고 명시적 승인 후 수행한다.

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
