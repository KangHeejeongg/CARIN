# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

**CAR IN (카인)** — 현대자동차그룹 Pleos Connect 차량용 앱 공모전 제출작 (구 이름 CAR IN NORAEBANG). 서비스 구조는 **카인 플레이리스트**(기존 플레이어 + Memory Route: 내비 목적지가 과거 여행지와 일치하면 "그날의 노래" 제안·재생) / **카인 노래방**(주차 중 사진 배경 + 정적 데모 가사) / **카인 추억**(여행 기록 목록·상세·사진) / CAR IN Together(2차).

- 언어: **Java 유지** (Kotlin 금지). 모든 UI는 XML 레이아웃 없이 코드로 생성한다 (`res/layout` 없음).
- 단일 모듈 `:app`, 단일 화면 `MainActivity` (`android.app.Activity` 상속, AppCompat 아님). 노래방도 별도 Activity가 아니라 같은 Activity 안의 오버레이(`karaoke/KaraokePanel`)다 — Activity를 나누면 `onStop()`에서 플레이어가 해제되어 음악이 끊긴다.
- 패키지명 `com.carin.noraebang`과 `applicationId`는 브랜드가 바뀌어도 유지한다.
- Git 저장소가 아니다(작업 디렉터리에 `.git` 없음). 사용자 승인 없이 `git init`/push/공개 설정을 하지 말 것.
- 팀명은 `CARIN`, 제출 APK는 `apk/CARIN_CAR_IN_release.apk`.

## 빌드 / 실행 명령

```bash
./gradlew assembleDebug                 # 디버그 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease               # 서명된 릴리스 APK (keystore.properties 필요, 없으면 unsigned)
./gradlew testDebugUnitTest             # JVM 단위 테스트 (PlaceMatcherTest 등)
./gradlew testDebugUnitTest --tests "com.carin.noraebang.PlaceMatcherTest"   # 단일 테스트 클래스
./gradlew lintVitalRelease              # release 빌드에 포함되는 lint
```

환경: Gradle 9.6 wrapper, AGP 9.5.1, compileSdk/targetSdk 37, minSdk 28, Java 11 소스 호환, 데몬 JVM toolchain 25 (Android Studio JBR `C:\Program Files\Android\Android Studio\jbr`). `org.gradle.configuration-cache=true`.

Pleos SDK는 `settings.gradle.kts`의 `https://nexus-playground.pleos.ai/repository/maven-releases/`에서 받는다 (`ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5`). SDK 소스는 `~/.gradle/caches/modules-2/files-2.1/ai.pleos.playground/**/*-sources.jar`에 있으므로 API가 궁금하면 추측하지 말고 거기서 확인한다.

### 에뮬레이터 / adb

```bash
emulator -avd Pleos_Connect_v2.1.0      # Android 14 (API 34) automotive, 2560x1440 @160dpi, user build
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.carin.noraebang/.MainActivity
adb logcat -s CARIN_NAVI CARIN_VEHICLE CARIN_SAFETY CARIN_MEMORY AndroidRuntime:E
```

- Pleos 에뮬레이터는 **user build**라 `cmd car_service inject-vhal-event`가 막혀 있고, 기어/속도는 벤더 속성이라 표준 `CarUxRestrictions`를 "주행 중"으로 바꿀 수 없다. 주행 제한 경로는 에뮬레이터에서 재현 불가.
- 앱은 운전자 사용자(user 10)로 실행된다. release 빌드는 데이터 디렉터리를 볼 수 없어 영속성을 force-stop 후 재실행으로 확인하고, **debug 빌드는 `adb shell run-as com.carin.noraebang --user 10 ls files/photos`처럼 `--user 10`을 붙이면 파일·prefs를 직접 확인할 수 있다**.
- debug 빌드 전용 실패 주입: `adb shell am start --user 10 -n com.carin.noraebang/.MainActivity --ez carin_debug_fail_photo_save true` (사진 연결 저장 실패), `--ez carin_debug_fail_photo_write true` (JPEG 쓰기 실패). release는 디버그 가능 플래그가 없어 무시된다.
- 에뮬레이터를 Bash 백그라운드로 띄우면 셸 종료 시 함께 죽는다. PowerShell `Start-Process`로 띄울 것.
- debug와 release는 서명이 다르므로 전환 설치 시 `adb uninstall com.carin.noraebang` 먼저.

### release 서명

`keystore.properties`(gitignore됨)가 루트에 있으면 `app/build.gradle.kts`가 읽어 release에 서명한다. 키스토어는 저장소 밖(`~/.android/carin-release.jks`)에 둔다. 형식은 `keystore.properties.example` 참고. 최종 제출 APK는 `apk/CARIN_CAR_IN_release.apk`로 복사한다.

Git Bash에서 `adb push/shell`에 `/data/...`, `/storage/...` 같은 절대 경로를 넘기면 MSYS가 `C:/Program Files/Git/...`로 바꿔 버린다. `export MSYS_NO_PATHCONV=1`을 앞에 붙일 것.

release의 R8 `optimization { packageScope = ... }`는 Pleos 에뮬레이터에서 `IllegalAccessError`(kotlin.collections 패키지-private 접근)로 앱이 시작 즉시 죽게 만들었으므로 다시 넣지 말 것.

## 아키텍처

```
MainActivity (메뉴 + 플레이어 + Memory Route + 노래방 오버레이 오케스트레이션)
 ├─ FrameLayout { LinearLayout(헤더·탭·페이지·미니 플레이어), KaraokePanel(GONE) }
 ├─ ExoPlayer (Media3 1.11.1)   onStart에서 생성, onStop에서 release
 ├─ memory/
 │   ├─ SongCatalog       번들 음원(res/raw) ↔ 고정 문자열 ID ↔ 제목. 플레이리스트의 단일 출처
 │   ├─ Memory            추억 1건 (id, place, title, travelDate, songId, createdAt, demo, photoUri?)
 │   ├─ MemoryStore       SharedPreferences("carin_memory_route") + JSON 배열 영속화. 손상 시 쓰기 차단·백업
 │   ├─ MemoryCodec       저장 원문 ↔ Memory. 손상 레코드 원문 보존 (JVM 테스트: MemoryCodecTest)
 │   ├─ PlaceMatcher      장소명 ↔ 목적지(name/address) 지역 단위 매칭
 │   ├─ MemoryListPage     "카인 추억" 탭의 사진 카드 그리드
 │   ├─ MemoryDetailDialog / MemoryRegisterDialog   CarinDialog 기반 상세(사진·재생·노래방·삭제)·입력 UI
 ├─ karaoke/
 │   ├─ KaraokePanel      노래방 전체 화면 뷰. 재생 제어는 Listener로 MainActivity에 위임
 │   ├─ DemoLyrics        팀 자체 제작 정적 데모 가사 (실제 가사 아님, 동기화 없음)
 │   └─ PhotoPicker       동작하는 사진 선택 인텐트 탐색(AAOS 스텁 제외)
 ├─ photo/
 │   ├─ PhotoImporter     새 사진: 크기 확인 → 축소(긴 변 1600) → EXIF 방향 보정 → 임시 파일에 JPEG → 이름 변경. 앱 소유 파일만 삭제
 │   └─ PhotoSizing       축소 크기 계산 (JVM 테스트: PhotoSizingTest)
 ├─ ui/                 디자인 시스템 (목업: Downloads/CARIN/목업, 브랜드 팔레트 시안)
 │   ├─ CarinTheme        팔레트 토큰(Deep Vinyl·Charcoal·Glass White·Light Rose·Coral)·뷰 팩토리. 색은 여기서만 정의
 │   ├─ CarinIcon         코드로 그리는 라인 아이콘 (이모지 대신)
 │   ├─ PillButton / CircleButton / ProgressLine / LogoView / RouteLineView(장식용, 실제 GPS 경로 아님)
 │   ├─ CarinDialog       시스템 AlertDialog 대신 쓰는 다크 로즈 다이얼로그
 │   └─ ImageLoader       사진 비동기 로딩. 사용자 사진 → assets/backgrounds/{hero|karaoke|memory}.* → default.* → 노을 그라디언트
 ├─ navi/NaviDestinationClient   NaviHelper SDK 래퍼 (연결, getDestinationInfo, 콜백을 메인 스레드로)
 └─ safety/DrivingSafetyGuard     android.car.CarUxRestrictionsManager를 리플렉션으로 조회
```

핵심 흐름과 불변 조건:

- **곡 식별은 `SongCatalog.Song.id`(예: `carin_song_01`)로만 한다.** raw 리소스 번호는 빌드마다 바뀔 수 있어 저장하지 않는다. `MainActivity.createSong()`이 `MediaItem.setMediaId(song.id)`를 넣고, `playMemorySong()`은 플레이리스트에서 `mediaId`가 같은 인덱스를 찾아 `player.seekTo(index, 0); player.play()`를 호출한다. 새 재생 경로를 만들지 말고 이 플레이어를 재사용한다.
- **목적지 출처를 구분한다.** `DestinationSource { NONE, SDK, DEMO }`. SDK 목적지(NaviHelper)가 비어 있지 않으면 데모 목적지보다 우선한다. UI 문구에도 `(SDK)` / `[DEMO]`를 항상 표기해 실제 연동과 데모를 혼동시키지 않는다.
- **메인 화면 구조.** 헤더(주행 상태 칩·로고·목적지 칩) → 탭 3개 → 페이지(플레이리스트: 히어로 카드 + 곡 목록 + Memory Route 패널 / 추억: `MemoryListPage`) → 하단 미니 플레이어. 노래방 탭은 오버레이를 연다.
- **추억 제안 중복 방지.** 추억 발견 시 팝업 대신 히어로 카드가 "지난번 이 길에서 들었던 노래가 있어요"로 바뀐다. `applyDestination()`은 `source|normalize(name)|normalize(address)` 키를 `lastPromptedKey`와 비교해 같은 목적지에는 탭 전환·강조를 한 번만 한다. 히어로는 항상 다시 그린다.
- **매칭은 substring이 아니라 토큰 완전 일치.** `PlaceMatcher`는 공백/쉼표로 토큰을 나누고 행정 접미사(시·군·구·역·광역시…)를 떼어 비교한다. "강릉" ↔ "강릉역"/"강원특별자치도 강릉시 …"는 일치, "산" ↔ "부산"은 불일치. 규칙을 바꾸면 `PlaceMatcherTest`를 함께 수정한다.
- **저장 원문을 덮어쓰지 않는다.** `MemoryStore`의 모든 쓰기는 `write()`를 거친다: 배열 전체 손상이면 차단(`isWriteProtected()`), 일부 손상 레코드는 원문 그대로 다시 저장. 새 쓰기 메서드를 만들 때 `saveAll` 같은 직접 덮어쓰기를 추가하지 말 것.
- **사진은 항상 앱 내부 복사본.** AAOS에서 `OPEN_DOCUMENT/GET_CONTENT`는 `com.android.car.frameworkpackagestubs` 스텁("파일 관리 미지원")으로 가고, 포토 피커(`android.provider.action.PICK_IMAGES`) URI는 영구 권한이 없다. 그래서 `PhotoImporter.importAsync()`로 `files/photos/<uuid>.jpg`에 만든 `file://` URI만 저장한다. 노래방 배경은 prefs `carin_karaoke`, 추억 사진은 `Memory.photoUri`. 변환이나 저장(추억 `update`, 노래방 prefs `commit`)이 실패하면 새 파일을 지우고 기존 연결을 유지한다. 저장에 성공했을 때만, 어떤 추억 원문(`MemoryStore.isPhotoReferenced`)이나 노래방 설정도 참조하지 않는 이전 파일을 지운다. 기존 사진 일괄 변환·고아 파일 일괄 정리는 아직 없다.
- **주행 안전.** 텍스트 입력·기록 삭제·노래방 진입·사진 선택 전에 `DrivingSafetyGuard.check()`를 호출하고 `PARKED`일 때만 진행한다. 노래방이 열린 채 `onResume`되면 다시 확인한다. `UNKNOWN`(차량 서비스 접근 불가)도 제한으로 취급한다. 속도 0만으로 주차라고 판단하지 않는다. 음악 재생은 제한하지 않는다. Vehicle SDK의 `Gear` API는 `@hide/@Deprecated`라 쓰지 않는다.
- **SDK 콜백은 백그라운드 스레드로 온다.** `NaviDestinationClient`가 `Handler(Looper.getMainLooper())`로 올린 뒤 콜백한다. Vehicle SDK 좌석 조회도 `runOnUiThread`로 감싼다. 둘 다 `onDestroy`에서 `release()`.
- NaviHelper 조회 권한 `pleos.car.permission.NAVI_ROUTE`(normal)은 main 매니페스트에 있다. `app/src/debug/`의 `NaviProbeActivity`는 SDK 진단용 debug 전용 화면이며 release에 포함되지 않는다.

## 하지 말 것 / 범위 밖

- Gleo AI, QR 공동 선곡, 서버, 로그인, SNS 공유, GPS 경로 기록, 가사, 마이크 노래방, 외부 스트리밍 API는 이번 범위가 아니다.
- 새 DB 라이브러리(Room 등)나 Gradle/의존성 업그레이드로 빌드를 흔들지 않는다.
- 저작권 음원을 추가하지 않는다. `res/raw/carin_song_0N.mp3`만 사용한다 (`carin_song_04`는 현재 카탈로그에 없음).
- 키스토어, 비밀번호, `local.properties`를 커밋하지 않는다.
