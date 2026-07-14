# Raspberry Pi Docker 운영 복구 런북

> 기준: 2026-07-14에 확인한 Pi 환경과 2026-05 복구 기록을 통합했다. IP, 디스크 장치명, image tag는 바뀔 수 있으므로 문서의 예시값을 그대로 믿지 말고 확인 명령의 결과를 기준으로 한다.

## 이 문서의 목적

OS 재설치, SD·SSD 교체 또는 Docker 장애 뒤에 홈서버를 다시 구성하는 순서와 각 서비스의 역할을 설명한다. 명령만 나열하지 않고 다음 내용을 함께 기록한다.

- 무엇을 하는 서비스인지
- 어떤 데이터와 설정을 보존해야 하는지
- 설치·실행 방법
- 정상 여부를 확인하는 방법
- 멈췄을 때 무엇이 영향을 받는지
- 민감정보를 어디에 두는지

## 현재 확인된 기반 환경

| 항목 | 확인된 값 | 의미와 주의점 |
|---|---|---|
| CPU | ARM64 (`aarch64`) | Yakmogo image도 `linux/arm64`로 빌드해야 한다. |
| Docker | 29.4.3 | 복구 시 실제 설치 버전을 다시 기록한다. |
| Docker Compose | v5.1.3 | `docker-compose`가 아니라 `docker compose`를 사용한다. |
| Docker data root | `/mnt/docker/docker-data` | 외장 디스크가 먼저 마운트되지 않으면 Docker를 시작하지 않는다. |
| MariaDB | host `127.0.0.1:3306` | bridge container에서는 접근할 수 없어 현재 Yakmogo는 host network를 사용한다. |
| Java | 21 | systemd 방식으로 되돌릴 때도 Java 21이 필요하다. |
| Tailscale | 설치됨 | 고정 IP라고 가정하지 말고 `tailscale ip -4`로 확인한다. |

비밀번호, Telegram token, GitHub secret, Uptime Kuma Push URL, VPN credential은 이 문서나 Git 저장소에 기록하지 않는다.

## 전체 복구 순서

1. 외장 디스크를 UUID 기준으로 마운트한다.
2. Docker Engine과 Compose를 설치하고 data root를 확인한다.
3. Transmission과 Gluetun을 복구한다.
4. Samba를 복구한다.
5. Java 21과 MariaDB를 복구한다.
6. Yakmogo DB·환경파일·애플리케이션을 복구한다.
7. Tailscale을 등록하고 현재 IP를 기록한다.
8. Jellyfin, Home Assistant, Syncthing을 복구한다.
9. Uptime Kuma를 마지막에 복구하고 실제 서비스와 monitor 목록을 대조한다.

Uptime Kuma를 마지막에 두는 이유는 감시 대상이 준비되기 전에 대량의 거짓 장애를 만들지 않기 위해서다.

## 1. 외장 디스크

### 역할

Docker data, 미디어, 다운로드와 일부 백업을 보관한다. 마운트가 빠지면 Docker가 빈 로컬 경로에 새 데이터를 만들 수 있으므로 가장 먼저 확인한다.

### 확인과 설정

```bash
lsblk -f
df -h
cat /etc/fstab
sudo mount -a
findmnt /mnt/docker
```

`/etc/fstab`에는 `/dev/sda1` 같은 장치명 대신 `UUID=...`를 사용한다. `sudo mount -a`가 오류 없이 끝난 뒤 재부팅하고 다시 `findmnt`로 확인한다.

### 장애 영향

- Docker daemon이 잘못된 위치를 사용하거나 시작에 실패한다.
- Transmission 다운로드·watch 경로가 사라진다.
- Jellyfin 미디어와 Samba 공유가 비어 보일 수 있다.

## 2. Docker Engine과 Compose

### 역할

Transmission/Gluetun, Jellyfin, Home Assistant, Syncthing, Uptime Kuma와 향후 Yakmogo 8081을 서로 분리된 서비스로 실행한다.

### 설치 원칙

Docker 공식 설치 절차로 Engine과 Compose plugin을 설치한다. 설치 명령은 배포판과 시점에 따라 달라질 수 있으므로 OS 버전을 확인하고 공식 절차를 따른다. 설치 후 운영 계정 `pi`만 Docker group에 둘 수 있다.

```bash
cat /etc/os-release
uname -m
docker version
docker compose version
docker info --format '{{.DockerRootDir}}'
```

`yakmogo-runner`를 Docker group에 넣지 않는다. Docker socket은 host root와 사실상 같은 권한이므로 GitHub Actions는 고정된 root helper만 좁은 sudoers 규칙으로 호출한다.

### 정상 확인

```bash
systemctl is-active docker
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker compose version
```

data root가 `/mnt/docker/docker-data`인지 확인하기 전에는 기존 Compose project를 올리지 않는다.

## 3. Compose 서비스 공통 복구법

각 서비스는 `/home/pi/docker/<service>/compose.yml`과 persistent volume 경로를 함께 백업한다.

```bash
cd /home/pi/docker/<service>
docker compose config
docker compose pull
docker compose up -d
docker compose ps
docker compose logs --tail 50
```

`docker compose config` 출력에는 환경변수의 실제 값이 펼쳐질 수 있으므로 외부에 복사하거나 CI 로그에 남기지 않는다. `latest` image는 복구 시 동작이 바뀔 수 있으므로 설치 후 실제 image digest를 운영 기록에 추가한다.

## 4. Transmission + Gluetun

### 역할

- Gluetun: VPN 연결과 외부 IP를 제공한다.
- Transmission: torrent 다운로드를 수행하며 Gluetun의 네트워크를 사용한다.

### 경로와 실행

```bash
cd /home/pi/docker/transmission-vpn
docker compose up -d
docker compose logs gluetun --tail 50
docker compose logs transmission --tail 50
```

Compose를 올리기 전에 downloads/complete, downloads/incomplete와 별도의 watch directory가 실제로 존재하고 권한이 맞는지 확인한다. watch directory는 download directory 안에 두지 않는다.

### 정상 조건과 장애 영향

- Gluetun 로그에 VPN 연결 지역과 외부 IP가 표시된다.
- Transmission UI가 설정된 9091 포트에서 열린다.
- Gluetun 장애 시 VPN을 우회해 Transmission만 실행하지 않는다.

## 5. Samba

### 역할

외장 디스크의 파일을 LAN 또는 Tailscale을 통해 공유한다.

```bash
sudo testparm
sudo systemctl enable --now smbd
systemctl is-active smbd
```

설정은 `/etc/samba/smb.conf`에 있으며 Linux 로그인 비밀번호와 Samba 비밀번호는 별개다. 사용자 등록은 서버 콘솔에서 `sudo smbpasswd -a pi`로 수행하고 비밀번호를 문서에 쓰지 않는다.

## 6. Java 21과 MariaDB

### 역할

- Java 21: Docker 전환 실패 시 systemd JAR을 실행하는 복구 수단이다.
- MariaDB: 운영 `yakmogo`와 고도화 `yakmogo_enhancement` 데이터를 보관한다.

```bash
java -version
systemctl is-active mariadb
sudo mariadb -e "SELECT VERSION();"
sudo ss -ltnp | grep 3306
```

MariaDB가 현재 `127.0.0.1:3306`에만 listen하는 것은 의도된 경계다. Docker 편의를 위해 bind address나 firewall을 임의로 변경하지 않는다. DB 사용자·권한을 복구한 뒤 백업 파일의 checksum을 검증하고 Goal 9 복원 런북에 따라 별도 테스트 DB에서 먼저 복원한다. `ddl-auto=update`만 믿고 운영 DB를 빈 상태에서 자동 생성하지 않는다.

## 7. Yakmogo

### 역할

복약 일정, 복약 상태 전이, 관리자 웹과 Telegram 알림을 제공한다.

### 현재 전환 상태

2026-07-14 기준으로 다음 세 실행 경계가 공존한다.

| 포트 | 실행 방식 | 용도 |
|---|---|---|
| 8080 | systemd `yakmogo.service` | 운영, 변경 금지 |
| 8081 | systemd `yakmogo-enhancement.service` | 현재 고도화 기준 |
| 18081 | Docker `yakmogo-enhancement` | 전환 전 shadow 검증 |

Docker 8081 cutover가 승인·완료되기 전에는 8081을 Docker 방식이라고 복구 문서에 확정해서 쓰지 않는다. 현재 shadow의 설치와 확인은 [enhancement Docker shadow 런북](enhancement-container-shadow.md)을 따른다.

### 중요한 경로

| 역할 | 경로 |
|---|---|
| Docker Compose | `/home/pi/docker/yakmogo-enhancement/compose.yml` |
| 비밀 환경파일 | `/etc/yakmogo/yakmogo-enhancement.env` (`root:root`, `600`) |
| systemd 운영 unit | `yakmogo.service` |
| systemd 고도화 unit | `yakmogo-enhancement.service` |
| runner staging | `/var/lib/yakmogo-container-deploy/incoming/` |

### 정상 확인

```bash
systemctl is-active yakmogo yakmogo-enhancement
docker inspect yakmogo-enhancement --format '{{.State.Running}} {{.Config.User}} {{.HostConfig.NetworkMode}}'
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18081/
```

프로세스가 active/running인 것만으로 준비 완료라고 판단하지 않는다. HTTP 200, 정적 JS 200, 보호 API 401까지 확인한다. 환경파일 전체나 `docker inspect`의 전체 Env를 출력하지 않는다.

## 8. Tailscale

### 역할

공개 포트포워딩 없이 휴대폰과 외부 장치가 홈서버에 접근할 수 있게 한다.

```bash
sudo tailscale up
tailscale status
tailscale ip -4
```

과거 IP 예시는 복구 기준이 아니다. 노드를 삭제하고 다시 등록하면 달라질 수 있으므로 현재 IP를 Uptime Kuma 대상과 사용자 접속 안내에 반영한다. 계정과 인증 키는 문서에 기록하지 않는다.

## 9. Jellyfin

### 역할

외장 디스크의 영상과 자막을 스트리밍한다. 기본 UI 포트는 8096이다.

```bash
cd /home/pi/docker/jellyfin
docker compose up -d
docker compose ps
docker compose logs --tail 50
```

미디어 bind mount가 실제 외장 디스크를 가리키는지 확인한다. 니코니코 comments에서 만든 ASS 자막은 `Noto Sans CJK JP`를 사용해도 Jellyfin Web에서 글자가 네모로 보일 수 있으며, 이는 container 폰트보다 Web client의 ASS/CJK 렌더링 한계일 수 있다. 이 경우 Jellyfin Media Player를 사용한다.

## 10. Home Assistant

### 역할

현재 Gaming PC의 Wake on LAN을 제공한다. WOL broadcast를 위해 host network를 사용한다.

```bash
cd /home/pi/docker/homeassistant
docker compose up -d
docker compose ps
docker compose logs --tail 50
```

설정 경로는 `/home/pi/docker/homeassistant/config/configuration.yaml`이다. `wake_on_lan:`과 switch의 MAC·LAN IP를 확인한다. MAC 주소는 유선 LAN 기준이며 외부에서는 Tailscale 연결 후 Home Assistant에 접속한다.

## 11. Syncthing

### 역할

지정한 장치 사이에서 파일을 동기화한다. 삭제도 동기화될 수 있으므로 백업과 동일한 것으로 취급하지 않는다.

Compose 경로, config/data volume과 장치 ID를 백업한다. 복구 후 바로 전체 동기화를 허용하기 전에 folder path와 send/receive 모드를 확인한다.

## 12. Uptime Kuma

### 역할

서비스가 정상인지 한 화면에서 보여 주고 장애를 알린다. Uptime Kuma 자체가 정상이어도 감시 대상이 정상이라는 뜻은 아니며, monitor마다 정상 조건과 장애 영향을 구분해야 한다.

### 복구

```bash
cd /home/pi/docker/uptime-kuma
docker compose up -d
docker compose ps
docker compose logs --tail 50
```

UI 기본 포트는 3001이다. persistent data volume이 복구되지 않으면 사용자, monitor, 알림 설정이 사라지므로 Compose 파일뿐 아니라 data volume도 백업한다.

### 이름과 설명 규칙

이름 형식은 `[서비스][환경] 검사대상`으로 통일한다.

```text
[Yakmogo][PROD] HTTP 8080
[Yakmogo][ENH] HTTP 8081
[CI/CD][PI] GitHub Runner heartbeat
[Backup][ENH-DB] Daily backup heartbeat
```

각 monitor의 설명 또는 비고에는 아래 항목을 그대로 채운다.

```text
감시 대상: 어떤 프로세스·URL·작업을 확인하는가
정상 조건: 어떤 응답 또는 heartbeat가 와야 하는가
장애 영향: 사용자 기능, 배포, 백업 중 무엇이 막히는가
확인 명령: Pi에서 처음 실행할 진단 명령
복구 문서: 저장소 런북 경로
소유/변경일: 담당 범위와 마지막 설정 변경일
```

설치된 Kuma 버전에 설명란이 없으면 태그로 서비스·환경·종류를 표시하고, 세부 설명은 아래 monitor 목록표에 유지한다.

### Monitor 목록표

Goal 9에서 실제 설정한 뒤 URL의 host와 Push token을 노출하지 않는 형태로 상태를 갱신한다.

| 이름 | 종류·주기 | 무엇을 확인하나 | 장애 영향 | 상태 |
|---|---|---|---|---|
| `[Yakmogo][PROD] HTTP 8080` | HTTP | 운영 웹 readiness | 실제 사용자 서비스 장애 | 기존 설정 조사 필요 |
| `[Yakmogo][ENH] HTTP 8081` | HTTP | 고도화 웹 readiness | 테스트·배포 검증 불가 | Docker cutover 후 확정 |
| `[CI/CD][PI] GitHub Runner heartbeat` | Push, 제안 2분 | runner systemd active와 GitHub HTTPS 연결 | 자동 배포·롤백 실행 불가, 앱 자체는 계속 동작 | Goal 9 승인 후 추가 |
| `[Backup][ENH-DB] Daily backup heartbeat` | Push | 고도화 DB 백업 작업 완료 | 최근 복구 지점 소실 | Goal 9 설계 후 추가 |

shadow 18081은 일시적인 전환 검증용이므로 장기 monitor로 추가하지 않는다. 필요하면 전환 기간에만 임시 monitor임을 이름과 설명에 명시하고 cutover 뒤 삭제한다.

### Runner heartbeat 원칙

- systemd timer가 2분마다 검사한다.
- runner 서비스가 active이고 GitHub HTTPS 연결이 가능할 때만 성공 heartbeat를 보낸다.
- Pi 전체가 꺼지면 요청을 보낼 수 없으므로 성공 heartbeat 누락으로 장애를 판정한다.
- Push URL은 token이므로 root 전용 환경파일에 두고 Git·로그에 남기지 않는다.
- runner에 Docker group, 임의 shell sudo 또는 Uptime Kuma 관리 권한을 주지 않는다.
- 프로세스 확인은 배포 성공을 완전히 보장하지 않으므로 하루 한 번 self-hosted runner 정기 workflow를 별도의 end-to-end 검사로 고려한다.

## 13. 백업 대상

최소한 다음을 외장 디스크 하나에만 의존하지 않는 위치에도 보관한다.

- 모든 `compose.yml`과 필요한 `.env`의 암호화된 사본
- Docker named volume 또는 bind-mounted persistent data
- MariaDB dump와 checksum
- `/etc/yakmogo/` 환경파일의 암호화된 사본
- Samba 설정과 필요한 systemd unit/helper
- Yakmogo release manifest와 직전 정상 image 또는 JAR
- Uptime Kuma data volume
- Home Assistant, Jellyfin, Syncthing 설정

백업 성공은 파일 존재가 아니라 checksum 확인과 격리된 복원 리허설로 판단한다. 주기, 보관 기간, 예상 디스크 사용량과 자동 삭제는 Goal 9 승인 후 이 문서에 실제 값으로 기록한다.

## 14. 복구 완료 점검

```bash
findmnt /mnt/docker
systemctl is-active docker mariadb smbd tailscaled
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker info --format '{{.DockerRootDir}}'
tailscale ip -4
df -h /mnt/docker
```

마지막으로 다음을 사람이 직접 확인한다.

- 8080 운영 화면과 인증 경계
- 8081 고도화 화면과 인증 경계
- Telegram과 scheduler가 의도한 환경에서만 활성인지
- Transmission이 Gluetun VPN을 우회하지 않는지
- Jellyfin 미디어 mount
- Home Assistant WOL
- Samba와 Syncthing의 실제 경로
- Uptime Kuma monitor 이름·설명·주기와 이 문서 목록의 일치
- 최신 DB 백업의 checksum과 마지막 복원 리허설 날짜

복구 과정에서 확인된 실제 버전, image digest, 변경된 경로와 시행착오는 같은 날 이 문서와 troubleshooting 문서에 함께 반영한다.
