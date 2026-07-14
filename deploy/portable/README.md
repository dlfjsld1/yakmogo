# Yakmogo 휴대형 설치 묶음

이 폴더 하나로 Yakmogo 앱과 전용 MariaDB를 함께 설치한다. 두 프로그램은 같은 Compose 프로젝트에 속하지만 서로 다른 컨테이너로 실행된다. DB 포트는 호스트에 공개하지 않으며 `yakmogo-app`만 지정한 HTTP 포트를 공개한다.

## 새 시스템 설치

필수 프로그램은 64비트 Linux, Docker Engine, Docker Compose 플러그인이다. Java, Node.js, MariaDB, GitHub Actions runner, Tailscale은 설치 대상 시스템에 필요하지 않다. 최초 설치 때 Docker가 MariaDB 이미지를 자동으로 받으므로 인터넷 또는 미리 받아 둔 `mariadb:11.8` 이미지가 필요하다.

```bash
cp .env.example .env
nano .env
chmod 600 .env
./install.sh
```

`.env`의 모든 `CHANGE_ME` 값을 바꾼다. `AUTH_TOKEN_SECRET`은 32자 이상의 무작위 값이어야 한다. Telegram 알림과 스케줄러는 안전을 위해 기본값이 꺼져 있다. 실제 사용 준비가 끝난 뒤 두 값을 `true`로 바꿔 `docker compose --env-file .env -f compose.yml up -d yakmogo-app`을 실행한다.

기존 DB 덤프를 새 빈 DB에 복원하며 설치하려면 다음처럼 실행한다.

```bash
./install.sh /안전한/경로/yakmogo-db.sql.gz
```

## 백업과 업데이트

```bash
./backup.sh
./update.sh /안전한/경로/yakmogo-새버전-linux-arm64.tar
```

업데이트는 먼저 DB를 백업하고 이미지 체크섬을 확인한다. 새 앱의 화면, 정적 파일, 인증 보호 API가 정상인지 확인하지 못하면 직전 이미지로 자동 복귀한다. DB 마이그레이션 자체를 역변환하지는 않으므로 중요한 업데이트 전에는 생성된 백업 파일을 별도 저장소에도 복사한다.

## 개발 PC에서 설치 묶음 만들기

빌드 PC에는 Java 21, Node.js, Docker Buildx, Git for Windows가 필요하다. 웹 프로젝트에서 먼저 `npm run build`를 실행한 뒤 백엔드 저장소에서 실행한다.

```powershell
.\scripts\release\build-image.ps1 -Version 0.0.7 -WebDist ..\yakmogo-web\dist
```

결과는 `build/portable` 폴더와 실행 권한을 보존하는 `build/yakmogo-<version>-portable.tar.gz`로 만들어진다. 새 시스템에는 압축 파일과 `.sha256`을 옮겨 체크섬을 확인한 뒤 압축을 푼다. CI도 같은 셸 스크립트를 사용하므로 수동 빌드와 CI 결과의 구조가 같다.

## 데이터 위치와 제거 주의

DB 데이터는 Docker named volume `yakmogo-mariadb-data`에 남는다. 컨테이너를 다시 만드는 것은 데이터를 지우지 않지만 `docker compose down -v`는 DB 볼륨을 삭제하므로 운영 환경에서는 실행하지 않는다.
