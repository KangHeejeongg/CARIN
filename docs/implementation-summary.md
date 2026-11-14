# 구현 요약 (Implementation Summary) — CAR IN

기존 음악 플레이어 앱 → Memory Route MVP → CAR IN 서비스 구조 → 목업 기준 UI 리디자인까지의 누적 구현 내역입니다.
원칙: Java 유지, XML 레이아웃 없이 코드로 UI 생성, 기존 ExoPlayer 재사용, 새 외부 의존성 없음, 패키지명/applicationId 유지.

## 1. 파일 구성

| 경로 | 역할 | 단계 |
|---|---|---|
| `MainActivity.java` | 헤더·탭·페이지·미니 플레이어·히어로 카드·노래방 오버레이를 묶는 단일 화면. 플레이어·목적지·안전·사진 로직 | 기존 + 확장 + 리디자인 |
| `memory/SongCatalog.java` | 번들 음원 ↔ 고정 ID(`carin_song_01`…) ↔ 제목. 플레이리스트와 추억이 공유하는 단일 출처 | MVP |
| `memory/Memory.java` | 추억 모델 + JSON (`photoUri` 선택 필드, 하위 호환) | MVP + CAR IN |
| `memory/MemoryStore.java` | SharedPreferences(JSON) 저장소. 조회/추가/수정/삭제/데모/매칭. 손상 시 쓰기 차단·백업(`isWriteProtected()`, `unreadableCount()`) | MVP + CAR IN + 저장 보호 |
| `memory/PhotoRetention.java` | 사진 삭제 가능 판단 (현재·직전·손상 백업 원문의 파일 이름 참조) | 사진 보존 |
| `memory/MemoryCodec.java` | 저장 원문 ↔ Memory 변환. 손상 레코드 원문 보존, 배열 손상 판정 (Android 비의존, JVM 테스트) | 저장 보호 |
| `memory/PlaceMatcher.java` | 지역 단위 매칭(토큰 + 접미사 제거 + 완전 일치) | MVP |
| `memory/MemoryListPage.java` | "카인 추억" 탭: 사진 카드 그리드, 빈 상태, 등록·데모 버튼 | 리디자인 |
| `memory/MemoryDetailDialog.java` | 추억 상세: 사진 + 경로선, 메타, 그날의 노래·노래방·사진 연결·삭제 | 리디자인 |
| `memory/MemoryRegisterDialog.java` | 등록 다이얼로그 (곡은 알약 탭으로 선택) | MVP + 리디자인 |
| `playback/CarinPlaybackService.java` | 재생 서비스(MediaLibraryService): 플레이어 소유, 탐색·검색·재개·포커스·종료 처리 | B1 |
| `playback/CarinPlaylist.java` | 탐색 구조와 곡 MediaItem·메타데이터 | B1 |
| `debug/.../MediaBrowseProbeActivity.java` | debug 전용 MBS 탐색·mediaId 재생 점검 | B1 |
| `karaoke/KaraokePanel.java` | 노래방 전체 화면 오버레이: 현재 줄 강조 가사, 진행 바, 원형 컨트롤 | CAR IN + 리디자인 |
| `karaoke/DemoLyrics.java` | 팀 자체 제작 정적 데모 가사 (`linesFor()`로 줄 단위 제공) | CAR IN |
| `karaoke/PhotoPicker.java` | 동작하는 사진 선택기 탐색 | CAR IN |
| `photo/PhotoImporter.java`, `photo/PhotoSizing.java` | 새 사진 크기 확인·축소·EXIF 방향 보정·JPEG 임시 저장 후 이름 변경, 앱 소유 파일 삭제 | 사진 관리 |
| `ui/CarinTheme.java` | 브랜드 팔레트 토큰과 뷰 팩토리(텍스트·유리 카드·칩·글로우·노을 그라디언트). **색은 여기서만 정의** | 리디자인 |
| `ui/CarinIcon.java` | 코드로 그리는 라인 아이콘 17종 (재생·마이크·핀·달력 등) | 리디자인 |
| `ui/PillButton.java` / `ui/CircleButton.java` | 알약 버튼(주·보조·탭) / 원형 버튼(코랄 재생·유리·투명) | 리디자인 |
| `ui/LogoView.java` | CAR IN 워드마크 (지붕 라인 + 자간 + 이퀄라이저) | 리디자인 |
| `ui/RouteLineView.java` | 장식용 경로선 + 핀 (실제 GPS 경로 아님) | 리디자인 |
| `ui/ProgressLine.java` | 재생 진행 바 (표시 전용) | 리디자인 |
| `ui/CarinDialog.java` | 시스템 AlertDialog를 대체하는 다크 로즈 다이얼로그 | 리디자인 |
| `ui/ImageLoader.java` | 사진 비동기 축소 디코딩 + LRU 캐시 + assets 기본 이미지 슬롯 | 리디자인 |
| `assets/backgrounds/README.txt` | 기본 이미지 슬롯 안내 (`hero`·`karaoke`·`memory`·`default`) | 리디자인 |
| `navi/NaviDestinationClient.java` | NaviHelper 3.0.5 래퍼 (메인 스레드 콜백) | MVP |
| `safety/DrivingSafetyGuard.java` | `CarUxRestrictionsManager` 리플렉션 조회 | MVP |
| `res/values(-night)/themes.xml`, `colors.xml` | `NoActionBar` + 브랜드 색 (상태바·창 배경 Deep Vinyl) | 리디자인 |
| `app/src/test/.../PlaceMatcherTest.java` | 매칭 단위 테스트 | MVP |
| `AndroidManifest.xml` | `NAVI_ROUTE` 권한, `android.car` uses-library(선택), 사진 선택기 `<queries>` | MVP + CAR IN |
| `app/build.gradle.kts` | `keystore.properties` 기반 release 서명, R8 `packageScope` 제거 | MVP |

삭제: `memory/MemoryListDialog.java` — 목록은 `MemoryListPage`(탭 화면), 상세는 `MemoryDetailDialog`로 대체.

## 2. 화면 구조

```
FrameLayout (앱 배경: Deep Vinyl + 상단 코랄 빛)
 ├─ LinearLayout
 │   ├─ 헤더         safetyStateText(칩) · LogoView · destinationChip(칩)
 │   ├─ 탭           PillButton×3 (TAB 스타일, 노래방 오버레이가 열리면 노래방 탭 강조)
 │   ├─ 페이지       FrameLayout
 │   │   ├─ playlistPage (ScrollView)
 │   │   │   ├─ 히어로 카드   사진/노을 + 스크림 + RouteLineView + 문구·버튼·점
 │   │   │   └─ [카인 플레이리스트 패널 | MEMORY ROUTE 패널(목적지·등록·데모·개발 정보)]
 │   │   └─ memoryPageScroll (ScrollView → MemoryListPage)
 │   └─ 미니 플레이어  썸네일 · 제목/순번/ProgressLine · ⏮ ▶ ⏭ · 목록
 └─ KaraokePanel (GONE)
```

## 3. 기능별 연결 구조

```
[탭]  createTabs()
   ├─ 카인 플레이리스트 → (노래방 닫기) selectTab(PLAYLIST), 맨 위로
   ├─ 카인 노래방      → openKaraoke(null)
   └─ 카인 추억        → (노래방 닫기) selectTab(MEMORY) → memoryPage.setMemories()

[추억 등록]  openRegisterDialog()
   └─ DrivingSafetyGuard.check()==PARKED → MemoryRegisterDialog → MemoryStore.add() → refreshMemoryViews()

[목적지 인식]
   SDK : NaviDestinationClient.connect() ─onConnected─▶ requestDestination()
          onDestinationInfo(info) ─main thread─▶ handleSdkDestination()  (빈 값이면 "설정된 목적지 없음")
          onDestinationChanged() ───────────────▶ requestDestination()
   DEMO: showDemoDestinationPicker() (CarinDialog) → applyDestination(DEMO, name, "")

[매칭 → 제안 → 재생]  applyDestination(source, name, address)
   ├─ key=source|normalize(name)|normalize(address); 새 key면 heroDismissed=false, heroIndex=0
   ├─ refreshMemoryViews(): MemoryStore.findMatching() → renderHero() · 목적지 칩 · 추억 탭 갱신
   └─ 매칭 있음 && key != lastPromptedKey → showMemoryPrompt(): 플레이리스트 탭 + 맨 위 + 히어로 강조 애니메이션
        renderHero() 상태:
          NONE        → "그 길을 다시 달릴 때…" + 내비 목적지 조회 / 데모 목적지 (+ 추억 등록)
          매칭 없음   → "'X'과(와) 연결된 추억이 없어요." + 추억 등록 / 다른 목적지
          매칭        → "지난번 이 길에서 들었던 노래가 있어요." + 재생하기 / 나중에
          나중에 이후 → "X에서의 추억 N개" + 그날의 노래 / 추억 보기
        재생하기 → playMemorySong(): mediaId 일치 인덱스 → player.seekTo(i,0); play()

[카인 추억]  MemoryListPage.Listener → MainActivity
   ├─ 그날의 노래 → playMemorySong
   ├─ 자세히 → showMemoryDetail() → MemoryDetailDialog
   │     ├─ 사진 연결 → (주차 확인) startPhotoPicker(REQ_PICK_MEMORY_PHOTO)
   │     │     └─ onActivityResult → PhotoPicker.importToAppStorage → MemoryStore.update(withPhoto) → 상세 다시 열기
   │     ├─ 노래방 → playMemorySong + openKaraoke(memory.photoUri)
   │     └─ 삭제 → confirmDeleteMemory(): (주차 확인) CarinDialog 확인 → MemoryStore.delete → 상세 닫기 → refreshMemoryViews
   ├─ 추억 등록 → openRegisterDialog
   └─ 데모 데이터 → createDemoData

[카인 노래방]  openKaraoke(photoOverride)
   ├─ DrivingSafetyGuard.check()==PARKED 아니면 토스트 후 중단
   ├─ karaokePanel.setSong / setPlaying / setProgress → applyKaraokeBackground() → 표시 → 탭 강조 갱신
   │     배경: 상세에서 들어온 추억 사진 → 현재 곡 songId의 최근 추억 사진 → prefs 노래방 사진 → assets karaoke → 그라디언트
   ├─ 패널 버튼 → togglePlay() / skipPrevious() / skipNext()  (미니 플레이어와 같은 메서드)
   ├─ Player.Listener → 패널 제목·가사(첫 줄부터)·재생 아이콘 동기화, progressTicker(0.5초) → 진행 바
   ├─ 가사 영역 터치 → 왼쪽 1/3 이전 줄, 나머지 다음 줄 (동기화 없음)
   ├─ 사진 선택 → startPhotoPicker(REQ_PICK_KARAOKE_PHOTO) → 내부 복사 → prefs 저장 → 배경 적용
   ├─ onBackPressed / ✕ → closeKaraoke()
   └─ onResume: 열려 있으면 UXR 재확인, 주차 아니면 닫음
```

## 4. 재생 구조 (Media3 MediaLibraryService)

```
CarinPlaybackService (MediaLibraryService, ExoPlayer 1개 소유)
 ├─ 생성: 곡 목록 준비, playWhenReady=false (자동 재생 없음)
 ├─ ExoPlayer: AudioAttributes(MEDIA/MUSIC, 포커스 자동 처리), 출력 분리 시 일시정지
 ├─ MediaLibrarySession ← MainActivity(MediaController), 차량 미디어 시스템(MBS), 미디어 버튼
 │   ├─ onGetLibraryRoot / onGetChildren: carin_root → carin_playlist → carin_song_0N
 │   ├─ onSearch / onGetSearchResult: 곡 제목 검색 (음성 연동용)
 │   ├─ onAddMediaItems: mediaId → URI 포함 항목
 │   ├─ onSetMediaItems: 곡 하나를 골라도 전체 목록 유지 + 그 곡부터
 │   └─ onPlaybackResumption: 마지막 곡·위치 (prefs carin_playback)
 ├─ 영구 포커스 상실 → stop / 재생 종료 → 다음 재개를 첫 곡으로
 └─ onTaskRemoved → 정지 → stopSelf, onDestroy → 플레이어·세션 해제
MainActivity
 ├─ onCreate: startService (Started 상태로 두어 onTaskRemoved 수신)
 ├─ onStart: SessionToken → MediaController.buildAsync → Player 리스너·화면 동기화
 └─ onStop: 리스너 제거, releaseFuture (재생은 서비스에서 계속)
```

- `togglePlay`/`skipPrevious`/`skipNext`/`playAt`은 `Player` 인터페이스(MediaController)만 사용한다. 곡 목록 행·추억 재생·노래방이 모두 이 메서드를 쓴다.
- Manifest: 서비스(`foregroundServiceType=mediaPlayback`, MediaLibraryService·MediaBrowserService 액션), `MediaButtonReceiver`, `FOREGROUND_SERVICE`·`FOREGROUND_SERVICE_MEDIA_PLAYBACK` 권한.
- 기준: [Pleos Music/Audio 앱 개발 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide).

## 5. 디자인 시스템 (`ui/`)

- 기준: 팀 목업(`Downloads/CARIN/목업`)과 브랜드 컬러 팔레트 시안의 다크 모드.
- 팔레트: Deep Vinyl `#1A191E`(배경) · Charcoal `#2B2B31`(카드) · Glass White `#FAF8F9`(텍스트) · Light Rose `#FFAEB5`(보조 강조) · Coral `#FF7F88`(주 액션). 데모·경고 표기는 Amber `#FFC98A`.
- 이미지 리소스를 추가하지 않고 아이콘·로고·경로선을 `Canvas`로 그린다. 글로우는 `setElevation` + `setOutlineSpotShadowColor`(API 28+).
- 기본 이미지: `ImageLoader`가 사용자 사진 → `assets/backgrounds/<slot>.(jpg|jpeg|png|webp)` → `default.*` → 뷰 배경(노을 그라디언트) 순으로 표시한다. 현재 슬롯 이미지는 비어 있다.

## 6. 저장 데이터 보호

| 원문 상태 | 읽기 | 쓰기(add/delete/update/createDemo) | 백업 | 화면 |
|---|---|---|---|---|
| 없음·빈 값 | 0건 | 정상 | — | — |
| 정상 배열 | 전체 | 정상. 저장 직전 원문을 `memories_prev`에 한 세대 보관 | — | — |
| 레코드 일부 손상 (필수 필드 누락, 객체가 아닌 값) | 읽을 수 있는 기록만 | 정상. 읽지 못한 레코드는 원래 JSON 값 그대로 함께 저장 | 처음 발견 시 `memories_backup`(최초 1회) + `files/memory_backups/memory_backup_<ms>.json` | 추억 탭 칩 "읽지 못한 기록 N개 · 원본 보존" |
| 배열 전체 손상 | 0건 | **차단**: 원문 변경 없이 false/null | 위와 같음 | 칩 "저장 데이터 보호 중 · 변경 차단", 등록·삭제·사진 연결·데모 생성 시 안내 토스트 |

- 반환형은 그대로이고, 실패 원인은 `MemoryStore.isWriteProtected()`로 구분한다. 호출부: `MainActivity`(`openRegisterDialog`, `onPickPhoto`, `confirmDeleteMemory`, `onActivityResult`, `createDemoData`), `MemoryRegisterDialog`.
- 같은 손상 원문은 해시로 한 번만 백업한다. `commit()` 실패 시 기존 원문이 유지되고 false를 반환한다.
- 검증: `MemoryCodecTest` 6건(빈 값, 정상 왕복, 이전 형식, 일부 손상 보존, 배열 손상, 배열이 아닌 최상위). JVM 테스트용으로 `testImplementation("org.json:json:20240303")`(APK 미포함).

## 7. 사진 처리

```
[추억 상세 > 사진 연결] onPickPhoto: 주차 확인 → 저장소 보호 확인 → startPhotoPicker(REQ_PICK_MEMORY_PHOTO)
[노래방 > 사진 선택]   onPickPhoto: 주차 확인 → startPhotoPicker(REQ_PICK_KARAOKE_PHOTO)
        ↓ PhotoPicker.buildIntent(): PICK_IMAGES → GET_CONTENT → OPEN_DOCUMENT → PICK (AAOS 스텁 제외)
onActivityResult: (추억이면 보호 상태 재확인) → PhotoImporter.importAsync (백그라운드)
   1) bounds 디코딩: 이미지 아님 → INVALID_IMAGE, 100MP 초과 → TOO_LARGE
   2) EXIF Orientation 읽기 (android.media.ExifInterface, 새 의존성 없음)
   3) inSampleSize(긴 변 ≥ 1600 유지) 디코딩 → Matrix로 긴 변 1600 + 회전·반전
   4) files/photos/.tmp_<uuid>.jpg 에 JPEG 85 → fsync → <uuid>.jpg 로 이름 변경 (실패 시 임시 파일 삭제)
        ↓ 메인 스레드
   실패 → 안내 토스트, 기존 연결 유지
   추억: applyMemoryPhoto → MemoryStore.update(withPhoto)
         실패(보호 상태/대상 없음/저장 실패) → 새 파일 삭제, 기존 연결 유지
         성공 → releaseReplacedPhoto(이전 URI): 삭제 대기(photos_pending_release)에 넣고 sweepReleasedPhotos()
   노래방: applyKaraokePhoto → prefs commit → 실패 시 새 파일 삭제 / 성공 시 이전 파일을 삭제 대기 + 정리

sweepReleasedPhotos() (추억 저장 성공 뒤마다: 등록·삭제·사진 연결·데모 생성)
   PhotoRetention.releasable(대기 목록, 보관본[현재 원문, memories_prev, memories_backup, memory_backups/*.json], 노래방 설정)
   → 어디에서도 파일 이름이 나오지 않는 앱 소유 파일만 삭제하고 대기 목록에서 제거
```

사진 교체 예: A → B 교체 직후 memories_prev가 A를 참조하므로 A 유지. 다음 저장(B → C)으로 prev가 B가 되면 A 삭제, B 유지.
따라서 직전 원문이나 손상 백업으로 복원하면 추억과 사진이 함께 돌아온다 (`PhotoRetentionTest`, 기기에서 memories ← memories_prev 복원 확인).

- Activity가 가져오는 도중 종료되면 새 파일을 지운다.
- 표시: `ImageLoader`가 백그라운드에서 축소 디코딩하고, 같은 뷰에 더 최근 요청이 있으면 이전 결과를 버린다.
- 범위 밖(후속): 기존 사진의 일괄 변환, 추억 삭제·노래방 "기본 배경" 시 파일 정리, 고아 파일 일괄 정리.
- 검증: `PhotoSizingTest`(축소 크기), 에뮬레이터에서 큰 사진·회전 사진·잘못된 이미지·같은 사진 재연결·재실행·덮어 설치, debug 실패 주입(저장 실패·JPEG 쓰기 실패)과 `run-as --user 10`으로 파일 목록 확인.

## 8. SDK / 플랫폼 API (소스 jar에서 확인한 것만 사용)

| SDK | 사용 API |
|---|---|
| NaviHelper 3.0.5 | `NaviHelper(ctx)`, `initialize(onConnected,onFailed)`, `addListener/removeListener`, `getDestinationInfo()`, `release()`; 콜백 `onDestinationInfo`, `onDestinationChanged`, `onError`; `DestinationInfo.getName()/getAddress()` |
| Vehicle 2.3.2 | (기존) `Vehicle.initialize()`, `getSeat().getCurrentSeatOccupancy()` — `Gear`는 `@hide/@Deprecated`라 미사용 |
| AAOS `android.car` | `Car.createCar`, `getCarManager("uxrestriction")`, `getCurrentCarUxRestrictions()`, `isRequiresDistractionOptimization()`, `getActiveRestrictions()`, `disconnect()` — 리플렉션 |
| Android | `android.provider.action.PICK_IMAGES`(문자열 상수), `ACTION_GET_CONTENT/OPEN_DOCUMENT/PICK`, `PackageManager.queryIntentActivities` + `<queries>` |
