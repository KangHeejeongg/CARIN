# MVP 감사 보고서 (CAR IN 전환 전 상태)

감사 시점: 2026-11-14 (CAR IN 브랜드 전환 작업 직전)

> 이 문서는 당시 상태의 기록입니다. 이후 2026-11-15 목업 기준 UI 리디자인으로 화면 구조·파일 구성이 바뀌었습니다(`MemoryListDialog` 삭제, `ui/` 추가, 추억 발견 팝업 → 히어로 카드). 현재 상태는 `feature-status.md`, `implementation-summary.md`, `change-summary.md`를 참고하세요.
검증 환경: Pleos Connect Emulator `Pleos_Connect_v2.1.0` (Android 14 / API 34, 2560×1440 @160dpi, user build)

상태 범례 — **DONE** 코드와 호출 흐름이 연결되고 에뮬레이터에서 동작 확인 · **PARTIAL** 일부만 구현 · **DEMO** 모의 데이터/선택으로 동작 · **NOT_STARTED** 미구현 · **BROKEN** 코드는 있으나 비정상 · **UNVERIFIED** 실행 검증 못 함

## 1. Git 상태

| 항목 | 결과 |
|---|---|
| 저장소 | **Git 저장소가 아님** (`fatal: not a git repository`). 브랜치/커밋/스테이지 정보 없음 |
| 작업 중인 변경 | 파일 시스템 기준으로 2026-11-14 15:58~16:25 사이에 Memory Route MVP 파일 일체가 추가/수정됨 (아래 3·4절) |
| 충돌 가능성 | Git 이력이 없으므로 머지 충돌은 없음. 단, 이 작업 디렉터리가 유일한 사본이므로 `rm`/덮어쓰기 금지 |
| 조치 | 이번 작업에서 `git init`/push는 하지 않음 (사용자 승인 필요). 제출 시 `.gitignore`가 `local.properties`, `keystore.properties`, `*.jks`를 제외함 |

## 2. Android 프로젝트 구조

| 항목 | 확인 내용 | 근거 |
|---|---|---|
| 모듈/화면 | 단일 모듈 `:app`, 단일 화면 `MainActivity extends android.app.Activity` | `app/src/main/java/com/carin/noraebang/MainActivity.java` |
| UI 방식 | XML 레이아웃 없음(`res/layout` 부재), Compose 없음. 모든 뷰를 Java 코드로 생성 (`createUI()`) | `MainActivity.createUI()` |
| 상태 관리 | ViewModel 없음. Activity 필드로 상태 보관 | — |
| 언어/빌드 | Java 11 소스, Gradle 9.6.0, AGP 9.5.1, compileSdk/targetSdk 37, minSdk 28, configuration-cache on | `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties` |
| Manifest | `MainActivity` LAUNCHER, `appCategory=audio`, `androidx.car.app.launchable=true`, 권한 `pleos.car.permission.CAR_SEATS`, `pleos.car.permission.NAVI_ROUTE`, `<uses-library android:name="android.car" required=false>` | `app/src/main/AndroidManifest.xml` |
| Pleos SDK 의존성 | `ai.pleos.playground:Vehicle:2.3.2`, `ai.pleos.playground:NaviHelper:3.0.5` (저장소 `nexus-playground.pleos.ai`) | `app/build.gradle.kts`, `settings.gradle.kts` |
| 테스트 | `PlaceMatcherTest` 3건 (JVM), 템플릿 `ExampleUnitTest`/`ExampleInstrumentedTest` | `app/src/test/...` |

## 3. 음악 플레이어

| 항목 | 확인 내용 | 근거 |
|---|---|---|
| 구현 위치 | `MainActivity.onStart()`에서 `new ExoPlayer.Builder(this).build()`, `onStop()`에서 `release()` | `MainActivity.onStart/onStop` |
| 재생 메서드 | `player.play()/pause()/seekToNextMediaItem()/seekToPreviousMediaItem()/seekTo(index,0)` | `setupButtons()`, `updatePlaylistUI()` 행 클릭 |
| 플레이리스트 구조 | `SongCatalog.all()` → `MediaItem`(uri `android.resource://pkg/rawId`, `mediaId = song.id`, title) | `SongCatalog`, `MainActivity.createSong()` |
| 음악 ID/URI | 고정 문자열 ID `carin_song_01~03` ↔ `R.raw.carin_song_0N`. `carin_song_04.mp3`는 raw에 존재하나 카탈로그 미등록 | `SongCatalog` |
| 재생 상태 | `Player.Listener`로 제목/순번/재생중 문구/버튼 라벨 갱신 | `MainActivity.onStart()` 리스너 |
| 상태 | **DONE** (테스트 1·8 통과) | |

## 4. 기존 MVP(Memory Route) 기능 상태

| 기능 | 상태 | 근거 파일 / 메서드 | 검증 |
|---|---|---|---|
| 추억 등록 UI (장소·제목·날짜·곡) | **DONE** | `memory/MemoryRegisterDialog.show()` | 에뮬레이터 등록 성공 토스트 확인 |
| 여행 장소 입력 / 날짜·제목 저장 | **DONE** | 동일 + `MemoryStore.create()/add()` | |
| 기존 음악과 추억 연결 | **DONE** | `Memory.songId` ↔ `SongCatalog.Song.id` ↔ `MediaItem.mediaId` | |
| 로컬 영속 저장 | **DONE** | `MemoryStore` — `SharedPreferences("carin_memory_route")` 키 `memories`에 JSON 배열 | `am force-stop` 후 재실행 시 "저장된 추억 2개" 유지 |
| 추억 목록 조회 / 상세 | **DONE** | `memory/MemoryListDialog.show()`, `showDetail()` | |
| 기록 삭제 | **DONE** | `MemoryListDialog` 삭제 → `MemoryStore.delete()` → `onChanged()` | 삭제 후 목록/카드 갱신 확인 |
| 목적지 조회 (NaviHelper) | **PARTIAL / UNVERIFIED(실데이터)** | `navi/NaviDestinationClient.connect()/requestDestination()` → `MainActivity.handleSdkDestination()` | 연결·조회 왕복 성공. 에뮬레이터에 목적지 없음 → `name/address` 빈 값 → "설정된 목적지 없음 · 조회 성공". 실제 목적지 값으로는 미검증 |
| 목적지 변경 이벤트 반영 | **UNVERIFIED** | `onDestinationChanged()` → `requestDestination()` | 이벤트를 발생시킬 내비 앱 없음 |
| 목적지–과거 여행 매칭 | **DONE** | `memory/PlaceMatcher.matches()`, `MemoryStore.findMatching()`, `MainActivity.applyDestination()` | 강릉역↔강릉 일치, 부산역 불일치 확인. 단위 테스트 3/3 |
| 추억 발견 알림 (팝업) + 중복 방지 | **DONE** | `showMemoryPrompt()`, `lastPromptedKey` | 동일 목적지 재선택 시 팝업 없음 |
| 그날의 음악 실제 재생 | **DONE** | `MainActivity.playMemorySong()` → `player.seekTo(i,0); play()` | debug/release 모두 `음악 재생 중 ♪` 확인 |
| 데모 목적지 선택 | **DEMO (DONE)** | `showDemoDestinationPicker()` — 6개 지명, UI에 `[DEMO]` 표기 | |
| 데모 데이터 생성 | **DONE** | `MemoryStore.createDemo()/hasDemo()` | 중복 생성 차단 확인 |
| 주행 안전 제한 | **PARTIAL** | `safety/DrivingSafetyGuard.check()` (리플렉션으로 `CarUxRestrictionsManager`), `ensureParkedForRestrictedFeature()` | 주차 상태(DO=false) 허용 확인. **주행 상태 차단은 UNVERIFIED** — user build라 VHAL 주입 불가 |
| 추억 사진 연결 | **NOT_STARTED** | — | |
| 노래방 화면 | **NOT_STARTED** | — | |
| QR/Together | **NOT_STARTED** (범위 외) | — | |

## 5. Pleos SDK / 플랫폼 연동

| 항목 | 상태 | 근거 |
|---|---|---|
| NaviHelper 실제 연동 | **DONE(연결·조회)** — 운영 코드 `NaviDestinationClient` | `initialize(onConnected,onFailed)`, `addListener`, `getDestinationInfo()`, `release()`. 콜백이 백그라운드 스레드로 와서 `Handler(mainLooper)`로 전달 |
| NaviHelper 실험 코드 | debug 전용 `app/src/debug/.../NaviProbeActivity.java` — release에 미포함. 운영 코드와 분리됨 | `app/src/debug/AndroidManifest.xml` |
| Vehicle SDK | **DONE** — 조수석 착좌 조회 (`Vehicle.initialize()`, `getSeat().getCurrentSeatOccupancy()`) | `MainActivity.initializeVehicle()/readSeatOccupancy()` |
| CarUxRestrictions | **DONE(조회)** — `android.car`가 SDK jar에 없어 리플렉션. 에뮬레이터 로그 `requiresDO=false active=0x0` | `DrivingSafetyGuard` |
| 에뮬레이터에서 가능한 것 | Navi 연결/조회, Vehicle 좌석 조회, UXR 조회(항상 주차), 음악 재생 | |
| 에뮬레이터에서 불가능한 것 | 내비 목적지 설정, 주행 상태 주입(`inject-vhal-event`는 non-user build 전용, 웹 콘솔 8088은 DDS 브리지 비활성 + 기어가 벤더 속성) | |

## 6. 재사용할 코드 / 새로 구현할 부분

재사용 (변경 없이 또는 최소 변경):
- 플레이어 전체 (`onStart/onStop`, `setupButtons`, `updateSongInfo`, `updatePlaylistUI`, `createSong`)
- `memory/*` 저장소·매칭·다이얼로그, `navi/NaviDestinationClient`, `safety/DrivingSafetyGuard`
- Memory Route 섹션(`createMemoryRouteSection()`, `applyDestination()`, `playMemorySong()`)

새로 구현 (CAR IN 전환):
- 앱 표시명/헤더/메뉴 문구를 CAR IN으로 변경 (패키지명·applicationId 유지)
- 메인 상단에 **카인 플레이리스트 / 카인 노래방 / 카인 추억** 진입점
- **카인 노래방** 최소 체험: 주차 상태에서만 진입, 사용자 사진 배경(없으면 기본 배경), 정적 데모 가사, 기존 플레이어 공유 — 같은 Activity 안의 전체 화면 오버레이로 구현해 플레이어 인스턴스를 그대로 공유
- **카인 추억** 사진 연결: `Memory.photoUri`(선택 필드, 기존 JSON과 호환), 상세에서 사진 표시, 노래방 배경으로 사용

## 7. 빌드에 영향을 줄 수 있는 위험

| 위험 | 내용 | 대응 |
|---|---|---|
| R8 `optimization.packageScope` | 기존 release 설정이 Pleos 에뮬레이터에서 `IllegalAccessError` 크래시 유발 (이전 세션에서 제거함) | 다시 넣지 않음. release 설치·실행으로 확인 |
| 플레이어 생명주기 | 별도 Activity로 노래방을 만들면 `onStop()`에서 플레이어가 해제되어 음악이 끊김 | 노래방을 MainActivity 내부 오버레이로 구현 |
| 사진 URI 권한 | `ACTION_OPEN_DOCUMENT` URI는 `takePersistableUriPermission` 없이는 재실행 후 접근 불가 | 선택 직후 영구 권한 획득, 로딩 실패 시 기본 배경 |
| AAOS 멀티유저 | 앱이 user 10으로 실행되어 `run-as`로 데이터 확인 불가 | force-stop 후 재실행으로 영속성 검증 |
| 다이얼로그 테마 | 시스템 기본(밝은) AlertDialog | 기능 영향 없음, 2차에서 통일 |
| 저장 데이터 호환 | `Memory` JSON에 필드 추가 시 기존 기록 파싱 실패 가능 | 새 필드는 `optString`으로 읽어 기존 데이터 유지 |

## 8. 실제 검증 결과 (CAR IN 전환 후, 2026-11-14)

| # | 시나리오 | 결과 | 근거 |
|---|---|---|---|
| 1 | 기존 음악 플레이어 정상 작동 | ✅ | 재생/일시정지/이전/다음, 플레이리스트 하이라이트 |
| 2 | 앱 표시 이름 CAR IN | ✅ | `aapt dump badging` `application-label:'CAR IN'` |
| 3 | 메인 메뉴 3개 접근 | ✅ | 플레이리스트(스크롤)·노래방(오버레이)·추억(목록) |
| 4 | 기존 노래로 추억 등록 | ✅ | 이전 세션 + 데모 데이터 생성으로 재확인 |
| 5 | 앱 재시작 후 추억 유지 | ✅ | `am force-stop` 후 재실행 |
| 6 | 강릉 목적지 → 저장된 강릉 추억 발견 | ✅ (데모 목적지) | 팝업 + 카드 |
| 7 | 추억 재생 버튼 → 실제 곡 재생 | ✅ | `음악 재생 중 ♪`, debug/release 모두 |
| 8 | 기록 없는 목적지에서 정상 동작 | ✅ | 부산역 → 빈 상태 카드 |
| 9 | 반복 조회 시 팝업 무한 반복 없음 | ✅ | 같은 목적지 재선택 시 팝업 없음 |
| 10 | 등록한 추억이 카인 추억 목록에 표시 | ✅ | |
| 11 | 상세 조회 · 삭제 | ✅ | 상세에 사진/메타, 삭제 후 즉시 갱신 |
| 12 | 플레이리스트와 동일 저장 데이터 | ✅ | 단일 `MemoryStore`, 별도 DB 없음 |
| 13 | 허용된 주차 상태에서만 노래방 접근 | ✅ 주차 / ⏳ 주행 | UXR 로그 `requiresDO=false`; 주행 상태 재현 불가 |
| 14 | 사용자 사진 배경 표시 | ✅ | 시스템 포토 피커 → 내부 복사 → 재실행 후 유지 |
| 15 | 사진 없으면 기본 배경 | ✅ | |
| 16 | 데모 가사 가독성 | ✅ | 반투명 패널 + 스크림, 비동기화 표기 |
| 17 | 노래방 재생 상태 = 플레이어 상태 | ✅ | 곡 전환 시 제목·가사·버튼 동기화 |
| 18 | Gradle Debug 빌드 | ✅ | `assembleDebug`, 단위 테스트 3/3 |
| 19 | Release 빌드 | ✅ | R8 + v2 서명, `apksigner verify` 통과 |
| 20 | Pleos Emulator 설치·실행 | ✅ | debug/release 모두 설치·실행·핵심 흐름 확인 |

## 9. 변경 전후 기능 대응표

| 변경 전 (CAR IN NORAEBANG) | 변경 후 (CAR IN) | 구현 방식 |
|---|---|---|
| 헤더 "CAR IN / NORAEBANG", 배지 "PARK DEMO" | 헤더 "CAR IN / 차 안에서 시작된 노래, 오래도록 남을 추억.", 배지 "MVP" | 문구 변경 |
| 앱 이름 `CARIN` | `CAR IN` | `strings.xml` |
| 메인 = 플레이어 + Memory Route | 메인 = 상단 메뉴(플레이리스트·노래방·추억) + 동일 플레이어 + Memory Route | 메뉴 행 추가, 화면 재사용 |
| "♫ 나의 플레이리스트" | "카인 플레이리스트" | 문구 변경 |
| "내 추억" 버튼 → 목록 다이얼로그 | "카인 추억" 메뉴/버튼 → 동일 목록 다이얼로그(+사진) | 재사용 |
| (없음) | 카인 노래방 오버레이 | 신규 `karaoke/` |
| `apk/CARIN_CAR_IN_NORAEBANG_release.apk` | `apk/CARIN_CAR_IN_release.apk` | 파일명 변경 (팀명 CARIN은 사용자 확인값) |
