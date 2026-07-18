# Yakmogo ARM64 Docker 배포 패키지

이 폴더 하나로 Yakmogo 앱과 전용 MariaDB를 함께 설치한다. 두 프로그램은 같은 Compose 프로젝트에 속하지만 서로 다른 컨테이너로 실행된다. DB 포트는 호스트에 공개하지 않으며 `yakmogo-app`만 지정한 HTTP 포트를 공개한다.

## 새 시스템 설치

실행 대상은 systemd를 사용하는 ARM64 GNU/Linux다. Docker Engine과 Docker Compose 플러그인이 필요하며 Java, Node.js와 MariaDB는 별도로 설치할 필요가 없다. 최초 설치 때 Docker가 MariaDB 이미지를 자동으로 받으므로 인터넷 또는 미리 받아 둔 `mariadb:11.8` 이미지가 필요하다. Docker 명령을 실행할 수 있고 `sudo` 권한이 있는 일반 사용자로 실행하며 `sudo ./setup.sh`는 사용하지 않는다.

```bash
./setup.sh
```

첫 실행에서는 감지한 LAN 주소를 기본 접속 URL로 제시하고 관리자 비밀번호만 입력받는다. DB 사용자·root 비밀번호와 인증 secret은 `/dev/urandom`으로 자동 생성해 권한 `600`인 `.env`에 저장한다. 이어서 앱·전용 MariaDB를 설치하고, 관리자 권한을 한 번 받아 매월 1일 03:35 KST 자동 백업을 등록한 뒤 health를 확인한다.

설치 스크립트가 변경하는 호스트 설정은 `yakmogo-backup.service`와 `yakmogo-backup.timer`뿐이다. Java, host MariaDB와 다른 Docker 서비스를 설치하거나 변경하지 않는다.

Telegram은 새 설치에서 기본 비활성 상태다. 사용하려면 Telegram의 `@BotFather`에서 봇을 만든 뒤 설치 폴더의 `.env`를 다음과 같이 수정한다. 봇 사용자명은 `@`를 제외하고 입력한다.

```dotenv
TELEGRAM_BOT_ENABLED=true
SCHEDULING_ENABLED=true
TELEGRAM_BOT_TOKEN=BotFather가_발급한_token
TELEGRAM_BOT_USERNAME=봇_사용자명
```

설정을 적용하려면 앱 container를 다시 만든다.

```bash
docker compose --env-file .env -f compose.yml up -d --force-recreate yakmogo-app
```

Telegram에서 새 봇을 열어 `/start`를 보내면 봇이 Chat ID를 알려 준다. 관리자 web에서 해당 사용자의 알림 수신자로 이 Chat ID를 등록한다. `.env`의 `TELEGRAM_CHAT_ID`는 이전 개발 환경과의 호환을 위한 값이므로 일반 설치에서는 `disabled`로 두어도 된다.

기존 DB 덤프를 새 빈 DB에 복원하며 설치하려면 다음처럼 실행한다.

```bash
./setup.sh /안전한/경로/yakmogo-db.sql.gz
```

기존 `.env`를 세밀하게 직접 관리하거나 timer 없이 격리 검증할 때만 저수준 `install.sh`를 사용한다. 일반 설치자는 `.env.example` 편집, `install.sh`, timer 설치 명령을 각각 실행할 필요가 없다.

## 백업과 업데이트

```bash
./backup.sh
./update.sh /안전한/경로/yakmogo-새버전-linux-arm64.tar
```

백업은 SQL dump와 같은 이름의 SHA-256 파일을 만든 뒤 둘을 다시 검증한다. 같은 백업 폴더에서 검증된 dump가 3개를 초과할 때만 가장 오래된 dump와 checksum을 삭제한다. 새 백업이 실패하면 기존 파일은 지우지 않는다. 동시에 두 백업이 실행되지 않도록 파일 잠금을 사용한다.

정기 백업은 `backups/scheduled`, 업데이트 직전 백업은 `backups/update`에 분리한다. 매월 1일 03:35(한국 시간) 정기 백업을 등록하려면 설치 사용자를 명시해 다음 명령을 한 번 실행한다. 이 명령은 systemd unit을 변경하므로 실제 서버에서는 검토와 승인 후 실행한다.

```bash
sudo ./install-backup-timer.sh "$USER"
systemctl list-timers yakmogo-backup.timer
journalctl -u yakmogo-backup.service
```

정기 백업 설정만 제거하고 기존 dump는 보존하려면 다음을 실행한다.

```bash
sudo ./remove-backup-timer.sh
```

업데이트는 먼저 전용 폴더에 DB를 백업하고 image checksum을 확인한다. 새 앱의 화면, 정적 파일, 인증 보호 API가 정상인지 확인하지 못하면 직전 image로 자동 복귀한다. DB migration 자체를 역변환하지는 않으므로 중요한 업데이트 전에는 생성된 백업 파일을 별도 저장소에도 복사한다.

## 상태 확인

`/actuator/health`는 애플리케이션과 DB 연결을 합친 전체 상태만 `UP` 또는 `DOWN`으로 반환한다. component 상세, 환경변수, 설정값과 heap dump endpoint는 공개하지 않는다.

```bash
curl -i http://127.0.0.1:8080/actuator/health
docker compose --env-file .env -f compose.yml ps
df -h .
```

## 개발 PC에서 설치 묶음 만들기

빌드 PC에는 Java 21, Node.js, Docker Buildx, Git for Windows가 필요하다. 웹 프로젝트에서 먼저 `npm run build`를 실행한 뒤 백엔드 저장소에서 실행한다.

```powershell
.\scripts\release\build-image.ps1 -Version 0.0.7 -WebDist ..\yakmogo-web\dist
```

결과는 `build/portable` 폴더와 실행 권한을 보존하는 `build/yakmogo-<version>-portable.tar.gz`로 만들어진다. 새 시스템에는 압축 파일과 `.sha256`을 옮겨 체크섬을 확인한 뒤 압축을 푼다. CI도 같은 셸 스크립트를 사용하므로 수동 빌드와 CI 결과의 구조가 같다.

## 데이터 위치와 제거 주의

DB 데이터는 Docker named volume `yakmogo-mariadb-data`에 남는다. 컨테이너를 다시 만드는 것은 데이터를 지우지 않지만 `docker compose down -v`는 DB 볼륨을 삭제하므로 운영 환경에서는 실행하지 않는다.
