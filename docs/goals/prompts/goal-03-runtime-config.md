# Goal 3 실행 프롬프트: 런타임 설정과 비밀정보 분리

[공통 고도화 원칙](common-principles.md)을 모두 적용한다.

## 목표

Java `-D` 인자와 `start.sh`에 노출된 비밀번호·token을 제거하고 운영과 고도화 런타임 설정 경계를 명확히 분리한다.

## 브랜치

최신 backend `enhancement`에서 `feature/runtime-config`를 만든다. web 변경이 없다면 불필요한 web 브랜치를 만들지 않는다.

## 작업

- systemd `EnvironmentFile` 또는 환경에 적합한 systemd 비밀 설정을 사용한다.
- 실제 비밀파일은 root 소유, 최소 mode 600으로 보호한다.
- 비민감 8081 설정은 `enhancement` Spring profile로 관리한다.
- 8081 포트, 별도 DB, bot 비활성, scheduler 비활성을 명시한다.
- `start.sh`에는 비밀값을 넣지 않고 `exec java -jar ...`만 남긴다.
- 실제 비밀번호와 token을 Git에 추가하지 않는다.
- placeholder만 있는 예제 환경파일을 제공한다.
- 기존 JAR, unit, start script를 백업한다.
- 최대 준비 시간을 둔 HTTP polling과 실패 시 롤백을 적용한다.
- README의 안전하지 않은 `-D` 예제를 수정한다.

## 완료 조건

- 8081 MainPID 명령행에 비밀번호·token·secret 없음
- 환경 디렉터리와 파일 권한 검증
- 8080과 8081 active 및 HTTP 200
- 8081 profile `enhancement`
- 8081 DB `yakmogo_enhancement`
- bot·scheduler 비활성
- 보호 API 무token 401
- backend test/bootJar와 CI 성공
- 배포·롤백·보안 한계 문서화

## 상태

완료. 실제 기록은 [Goal 3 문서](../goal-03-runtime-config.md)를 참고한다.
