# RunCoach 페이스별 음악 추천 기능 PRD

> 2026-09-26 검토: 아래 자동 재생·전환, 로그인·구독 조건은 검증 전 가정입니다. 첫 실험은 사용자가 선택한 플레이리스트 열기로 제한하고 실기기에서 검증합니다. 심박수 상승만으로 운동 독려나 음악 전환을 결정하지 않습니다. [현재 우선순위](docs/DIFFERENTIATION_STRATEGY.md)를 참고하세요.

> **작성일:** 2026-07-03
> **버전:** v1.0
> **플랫폼:** Galaxy Watch 5/6/7/8 (Wear OS)
> **연동 서비스:** YouTube Music (YouTube Premium 구독자)

---

## 1. 배경 및 목적

### 배경
50~60대 러너는 달리기 중 음악 선택에 손이 많이 간다.
워치 화면을 보며 터치로 곡을 바꾸는 행위는 달리기 리듬을 끊고, 낙상 위험도 있다.

### 목적
- 워치가 현재 페이스/심박수를 감지해 러닝 페이즈(초반/중반/종반)를 자동 판단
- TTS 음성으로만 음악을 추천하고, 터치 1번으로 재생
- 별도 설정 없이 앱 실행 즉시 작동

---

## 2. 핵심 제약 조건

| 조건 | 내용 |
|------|------|
| 인터페이스 | 워치 전용 — 폰 설정 화면 없음 |
| 입력 방식 | TTS 음성 안내 + 터치 최소화 |
| 최대 탭 수 | 앱 실행부터 음악 재생까지 총 3탭 이내 |
| 설정 | 제로 설정 — 첫 실행부터 바로 동작 |
| 음악 서비스 | YouTube Music (YouTube Premium 구독자 대상) |
| 음악 제어 | 재생 시작/플레이리스트 전환만 — 곡별 제어 없음 |

---

## 3. 사용자 시나리오

### 메인 시나리오 (러닝 시작 시)

```
[탭 ①] 워치 알림 탭 → 런코치 실행
[탭 ②] 홈 화면 → "시작" 탭
         ↓
    PreRun 화면 진입
    TTS: "음악 켤까요?  초반 케이팝 댄스 추천드려요"
         ↓
[탭 ③] [✅ 음악 켜고 시작] 탭
         ↓
    러닝 시작 + YouTube Music 자동 실행
    TTS: "초반 댄스 재생합니다. 달려볼까요!"
```

**총 탭 3번. 추가 설정 0.**

---

### 페이즈 변경 시나리오 (러닝 중, 자동)

```
[중반 감지]
  조건: 경과 시간 40% 이상 OR 평균 심박 > 150bpm
  TTS: "중반입니다. 발라드로 바꿀게요"
  → 3초 후 YouTube Music 자동 전환
  → 화면: [⏭ 유지] 탭 시 전환 취소

[종반 감지]
  조건: 경과 시간 70% 이상 OR 평균 심박 > 165bpm
  TTS: "종반! 락발라드 틀게요. 힘내세요!"
  → 3초 후 YouTube Music 자동 전환
```

**페이즈 변경은 탭 없음. 유지하고 싶을 때만 1탭.**

---

## 4. 페이즈 정의 및 음악 매핑

### 페이즈 판단 로직

```
진행률 = 현재 거리 / 목표 거리

EARLY (초반): 진행률 < 35% AND 평균 심박 < 150bpm
MID   (중반): 진행률 35%~70%
LATE  (종반): 진행률 > 70% OR 평균 심박 > 165bpm
```

### 페이즈별 음악 컨셉 및 유튜브 뮤직 쿼리

| 페이즈 | 컨셉 | 대상 심리 | YouTube Music 검색 쿼리 |
|--------|------|----------|------------------------|
| 초반 EARLY | 트렌디한 경쾌한 댄스 | 힘이 있을 때, 리듬 타기 | `케이팝 댄스 최신` |
| 중반 MID | 멜로디 좋은 추억 히트곡 | 숨이 차오를 때, 감성으로 버티기 | `한국 발라드 히트` |
| 종반 LATE | 락/락발라드, 감정 고조 | 힘들 때, 감정으로 극복 | `한국 락 발라드` |

> 장르는 앱에 하드코딩. 로그인/설정 불필요.
> YouTube Premium 있으면 광고 없이 즉시 재생. 없으면 광고 후 재생.

---

## 5. UX 플로우 상세

### 5-1. PreRun 화면 (변경)

```
┌─────────────────────────────────┐
│  PreRun                         │
│                                 │
│  지난주  5.0km  6:27/km  ✅    │
│  오늘    5.5km  6:20/km 목표   │
│                                 │
│  ─────────────────────────────  │
│                                 │
│  🎵 초반 케이팝 댄스            │← TTS로 읽음
│     YouTube Music 재생 예정     │
│                                 │
│  ┌─────────────┐ ┌───────────┐  │
│  │✅ 음악 켜고 │ │🏃 그냥    │  │
│  │   시작      │ │   시작    │  │
│  └─────────────┘ └───────────┘  │
└─────────────────────────────────┘
```

**TTS 스크립트:**
> "지난주 5킬로 달리셨어요. 오늘 목표는 5.5킬로입니다.
> 음악 켤까요? 초반 케이팝 댄스 추천드려요."

---

### 5-2. 러닝 중 페이즈 오버레이

```
┌─────────────────────────────────┐
│  [러닝 화면 배경]               │
│   3.8km  6:24/km  152bpm       │
│                                 │
│  ┌─────────────────────────┐    │
│  │ 🎵 중반 구간            │    │
│  │ 발라드로 바꿀게요        │    │
│  │                         │    │
│  │  3초 후 자동 전환...    │    │
│  │           [⏭ 유지]      │    │
│  └─────────────────────────┘    │
└─────────────────────────────────┘
```

**오버레이 지속 시간:** 5초 후 자동 닫힘  
**[⏭ 유지] 탭:** 플레이리스트 전환 취소, 현재 음악 유지

---

### 5-3. 러닝 종료 후

```
TTS: "오늘 러닝 수고하셨어요. 음악 종료합니다."
→ 폰에서 YouTube Music 일시정지
```

---

## 6. 기술 아키텍처

### 데이터 흐름

```
[워치] WearRunningService
  실시간: distanceKm, heartRate, elapsedSec
        ↓
[워치] RunPhaseDetector
  페이즈 판단: EARLY / MID / LATE
  페이즈 변경 감지 → 이벤트 발행
        ↓
[워치] MusicTtsController
  TTS 안내: BriefingTtsManager 재사용
  오버레이 표시: MusicPhaseOverlay
        ↓ 사용자 탭 (✅ 또는 자동)
[워치→폰] Wearable Data Layer
  경로: /music/play
  payload: { phase: "MID", query: "한국 발라드 히트" }
        ↓
[폰] WearDataListenerService (기존)
  + music 명령 핸들러 추가
        ↓
[폰] YouTube Music Intent 실행
  data: https://music.youtube.com/search?q=한국+발라드+히트
  package: com.google.android.apps.youtube.music
```

### 워치→폰 메시지 경로 추가

| 경로 | 방향 | 내용 |
|------|------|------|
| `/music/play` | 워치→폰 | 페이즈 + 검색쿼리 (재생 시작) |
| `/music/pause` | 워치→폰 | 러닝 종료 시 일시정지 |
| `/music/keep` | 워치→폰 | 페이즈 변경 시 현재 유지 선택 |

---

## 7. 구현 컴포넌트

### 신규 파일

```
wear/
└── ui/
    └── running/
        ├── RunPhaseDetector.kt       ← 페이즈 판단 로직 (~30줄)
        └── MusicPhaseOverlay.kt      ← 오버레이 Composable (~50줄)

app/
└── service/
    └── MusicLaunchHandler.kt         ← YouTube Music 인텐트 실행 (~20줄)
```

### 수정 파일

```
wear/
├── ui/prerun/PreRunScreen.kt         ← 음악 추천 카드 + 버튼 2개 추가
├── ui/running/RunningScreen.kt       ← MusicPhaseOverlay 훅 추가
├── ui/running/RunningViewModel.kt    ← 페이즈 변경 이벤트 추가
└── tts/BriefingTtsManager.kt         ← 음악 추천 TTS 스크립트 추가

app/
└── service/WearDataListenerService.kt ← /music/* 경로 핸들러 추가
```

---

## 8. 핵심 로직 스펙

### RunPhaseDetector

```kotlin
enum class RunPhase { EARLY, MID, LATE }

fun detectPhase(
    currentKm: Float,
    targetKm: Float,
    elapsedSec: Int,
    avgHeartRate: Int
): RunPhase {
    val progress = if (targetKm > 0) currentKm / targetKm else 0f
    return when {
        progress < 0.35f && avgHeartRate < 150 -> RunPhase.EARLY
        progress < 0.70f                        -> RunPhase.MID
        else                                    -> RunPhase.LATE
    }
}

fun getMusicQuery(phase: RunPhase): Pair<String, String> = when (phase) {
    RunPhase.EARLY -> Pair("초반 케이팝 댄스",  "케이팝 댄스 최신")
    RunPhase.MID   -> Pair("중반 발라드 히트곡", "한국 발라드 히트")
    RunPhase.LATE  -> Pair("종반 락 발라드",     "한국 락 발라드")
}
```

### TTS 스크립트 정의

| 상황 | TTS 내용 |
|------|----------|
| PreRun 진입 시 | "음악 켤까요? {페이즈명} 추천드려요" |
| 음악 켜고 시작 | "{페이즈명} 재생합니다. 달려볼까요!" |
| 중반 자동 전환 | "중반입니다. 발라드로 바꿀게요" |
| 종반 자동 전환 | "종반! 락발라드 틀게요. 힘내세요!" |
| 페이즈 유지 선택 | "현재 음악 유지합니다" |
| 러닝 종료 | "수고하셨어요. 음악 종료합니다" |

---

## 9. YouTube Music 인텐트

```kotlin
fun launchYoutubeMusic(context: Context, query: String) {
    val uri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")

    // YouTube Music 앱으로 실행 시도
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.youtube.music")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    // YouTube Music 미설치 시 브라우저 폴백
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }
}
```

---

## 10. 구현 순서

```
1단계  PreRunScreen 음악 카드 + 버튼 추가
       └─ TTS 스크립트 연결 (BriefingTtsManager)

2단계  RunPhaseDetector 구현
       └─ RunningViewModel에 페이즈 StateFlow 추가

3단계  /music/play 워치→폰 메시지 경로 추가
       └─ WearDataListenerService 핸들러 추가

4단계  MusicLaunchHandler (폰) 구현
       └─ YouTube Music 인텐트 실행

5단계  MusicPhaseOverlay (워치) 구현
       └─ RunningScreen에 페이즈 변경 훅 연결

6단계  러닝 종료 시 /music/pause 전송
```

---

## 11. 비고 및 향후 확장

### 현재 범위 (v1.0)
- 하드코딩 장르 3개 (초반/중반/종반)
- YouTube Music 검색 결과 재생 (개인 플레이리스트 아님)
- 로그인/설정 없음

### 향후 확장 (v2.0)
- Google OAuth 연동 → 사용자 개인 플레이리스트 연결
- 워치 설정 화면에서 페이즈별 플레이리스트 직접 지정
- 러닝 후 "오늘 음악 어땠나요?" TTS 피드백 수집
- 심박수 급등 시 자동으로 차분한 음악 전환 (안전 기능)
