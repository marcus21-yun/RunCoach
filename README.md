# RunCoach 앱 — 프로젝트 정리

> 2026-09-26 저장소 정리: 현재 소스는 `app/`, `wear/`, `core/`와 `supabase/`에 있습니다.
> 다음 개발 방향은 [기능 개선 계획](docs/IMPROVEMENT_ROADMAP.md)을 참고하세요.
> 아래 설명은 기존 개발 기록이므로 현재 구현과 일부 다를 수 있습니다.
>
> 로컬 설정: `local.properties.example`을 `local.properties`로 복사해 SDK 경로와 키를 입력하세요.
> `gradle.properties`의 `org.gradle.java.home`은 기존 Windows 개발 환경 경로이므로 자신의 JDK 17 이상 경로에 맞춰야 합니다.

> 갤럭시 워치5 올인원 | 하프마라톤 1년 도전 러닝 코칭 앱
> 최초 작성: 2026-03-18 | 최종 수정: 2026-03-21

---

## ⚠️ 아키텍처 변경 사항 (2026-03-21)

> **모든 UI는 갤럭시 워치5에서 구현한다.**

| 구분 | 변경 전 | 변경 후 |
|------|---------|---------|
| 폰 앱 | 5개 화면 (메인 UI) | 백그라운드 서비스만 (UI 없음) |
| 워치 앱 | 알람 4종만 | **전체 UI + 알람** (메인) |
| 사용자 접점 | 폰 화면 위주 | 워치 화면 100% |

---

## 프로젝트 개요

### 목표
- 매주 일요일 러닝을 습관으로 만들고 **1년 뒤 하프마라톤(21.0975km) 완주**
- **갤럭시 워치5 하나로** 러닝 전/중/후 모든 정보 확인
- 폰을 꺼내지 않고 손목에서 1탭으로 모든 것을 해결

### 핵심 가치
- **워치 올인원** — 폰 없이 워치만으로 목표 확인 + 기록 + 코칭
- **실시간 코칭** — 뛰는 중 소리+진동으로 즉각 안내
- **AI 성장 관리** — 매주 기록 기반으로 다음 목표 자동 제안

---

## 기획 문서 목록

| 파일 | 내용 | 상태 |
|------|------|------|
| [2_RunCoach-Screen-Flow.md](.claude/MVP/2_RunCoach-Screen-Flow.md) | 전체 화면 흐름도 | 업데이트 필요 |
| [3_RunCoach-Watch-Wireframe.md](.claude/MVP/3_RunCoach-Watch-Wireframe.md) | 워치 알람 와이어프레임 | 유효 |
| [4_RunCoach-Home-Wireframe.md](.claude/MVP/4_RunCoach-Home-Wireframe.md) | 폰 앱 와이어프레임 | → 워치로 전환 필요 |
| [5_RunCoach-AI-Logic.md](.claude/MVP/5_RunCoach-AI-Logic.md) | AI 목표 제안 로직 | 유효 |
| [6_RunCoach-SamsungHealth-SDK.md](.claude/MVP/6_RunCoach-SamsungHealth-SDK.md) | Samsung Health SDK | 유효 |
| [7_RunCoach-TechStack-MVP.md](.claude/MVP/7_RunCoach-TechStack-MVP.md) | 기술 스택 및 MVP | 업데이트 필요 |

---

## 앱 구조

### 역할 분리

```
[Galaxy Watch 5]  ← 모든 UI + 센서 + 알람
      │ Wearable Data Layer API
      ▼
[Android 폰]  ← 백그라운드 서비스만
      │ Samsung Health SDK
      ▼
[Samsung Health 앱]  ← 건강 데이터 저장소
```

---

## 워치 앱 — 전체 UI (5개 화면)

> Galaxy Watch 5 화면: 원형 396×396px | 크라운 회전 + 탭 + 스와이프 조작

---

### 화면 1. 홈 대시보드

```
╭─────────────────╮
│  RunCoach  ⚙    │
│                 │
│  D-364일        │
│  ████░░ 12%     │
│                 │
│  이번주 5.0km   │
│  6:30/km 목표   │
│                 │
│  [▶ 시작]       │
╰─────────────────╯
크라운 아래 → 기록 화면
```

---

### 화면 2. 러닝 시작 전

```
╭─────────────────╮
│ ← 오늘 목표     │
│                 │
│ 지난주          │
│ 4.5km 6:45/km  │
│                 │
│ 오늘 (AI제안)   │
│ 5.0km 6:30/km  │
│ 3km↑ +5%       │
│                 │
│ [수정]  [시작!] │
╰─────────────────╯
```

---

### 화면 3. 러닝 중 (메인)

```
╭─────────────────╮
│   3.2 km        │
│ ─────────────── │
│ 6:31/km  ♥158  │
│ ─────────────── │
│ 지난주 +0.4↑    │
│                 │
│ 23:14  피로:보통│
╰─────────────────╯
```

**실시간 알람 4종 (뛰는 중)**

| 알람 | 트리거 | 진동 | 표시 |
|------|--------|------|------|
| 거리 달성 | 1km마다 | 1회 짧게 | 🔔 1km! 페이스 🟢 |
| 심박 경고 | 170bpm 초과 | 3회 강하게 | ⚠️ 심박 178 높음 |
| 구간 목표 | 설정 구간 도달 | 2회 중간 | 🎯 3km! 스피드업 |
| 페이스 이탈 | 목표보다 10% 느릴 때 | 2회 | 📉 페이스 저하 |

알람 우선순위: 심박 경고 > 구간 목표 > 거리 달성 > 페이스 이탈

---

### 화면 4. 러닝 후 결과

```
╭─────────────────╮
│ 🎉 완료!        │
│                 │
│ 5.1km  32:45   │
│ 6:27/km  ✅    │
│ ♥155  피로:높음│
│                 │
│ 지난주 +0.6↑   │
│                 │
│ [AI 다음목표 →] │
╰─────────────────╯
```

---

### 화면 5. AI 다음 목표 제안

```
╭─────────────────╮
│ 🤖 다음주 제안  │
│                 │
│ 피로 높았어요   │
│ 거리 유지할게요 │
│                 │
│ 5.0km 유지      │
│ 6:25/km         │
│ 4km↑ +3%       │
│                 │
│ [수락] [수정]   │
╰─────────────────╯
```

---

### 화면 6. 기록 히스토리 (크라운 스크롤)

```
╭─────────────────╮
│ 📊 기록         │
│                 │
│ 3/16 5.1km ✅  │
│ 3/ 9 4.5km ✅  │
│ 3/ 2 4.0km ✅  │
│                 │
│ 하프마라톤      │
│ 예상 38주 후    │
╰─────────────────╯
```

---

## 워치 화면 전환 흐름

```
[일요일 아침 워치 알림]
        │ 탭
        ▼
[홈 대시보드]
        │ 시작 버튼
        ▼
[러닝 시작 전]
        │ 시작 버튼
        ▼
[러닝 중 메인] ←── 실시간 알람 오버레이
        │ 종료 버튼 (길게 누르기)
        ▼
[러닝 후 결과]
        │ AI 제안 버튼
        ▼
[AI 다음 목표]
        │ 수락
        ▼
[홈 대시보드]

크라운 회전:
  홈 → 기록 히스토리 스크롤
  러닝 중 → 추가 데이터 (칼로리/케이던스)
```

---

## AI 목표 제안 로직

### 입력 데이터
완주 여부 / 실제 거리 / 평균 페이스 / 평균 심박수 / 피로도

### 판단 기준
| 상태 | 다음 주 거리 | 페이스 |
|------|-------------|--------|
| 피로 높음 / 심박 과부하 | 유지 | 5% 느리게 |
| 완주 실패 | -0.5km | 유지 |
| 성공 + 적정 | +0.5km | 5초 빠르게 |
| 성공 + 여유 | +1.0km | 10초 빠르게 |

### 하프마라톤 역산
```
주당 필요 증가량 = (21.0975 - 현재거리) / 남은주수
→ AI 제안이 너무 빠르거나 느리면 자동 보정
```

---

## 기술 스택

### 워치 앱 (Galaxy Watch 5) — 메인
| 항목 | 기술 |
|------|------|
| 플랫폼 | Wear OS 3.5 (Galaxy Watch 5) |
| 언어 | Kotlin |
| UI | **Wear Compose** (전체 화면) |
| 센서 | Health Services API |
| 내비게이션 | Wear Navigation Compose |
| 로컬 저장 | DataStore (워치 내 기록 캐시) |
| 폰 통신 | Wearable Data Layer API |
| 알람/진동 | VibratorManager + SoundPool |

### 폰 앱 (Android) — 백그라운드 서비스만
| 항목 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | **없음** (백그라운드 전용) |
| 아키텍처 | Service + BroadcastReceiver |
| DB | Room (SQLite) — 기록 영구 저장 |
| 건강 데이터 | Samsung Health SDK |
| 주간 알람 | AlarmManager → 워치에 신호 전송 |
| 백그라운드 | Foreground Service |

---

## 프로젝트 파일 구조

```
RunCoach/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
│
├── wear/                               ← ★ 메인 (워치 앱 — 전체 UI)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/runcoach/wear/
│           ├── WearMainActivity.kt     ← 워치 진입점 + 네비게이션
│           ├── ui/
│           │   ├── home/
│           │   │   ├── HomeScreen.kt          ← 홈 대시보드
│           │   │   └── HomeViewModel.kt
│           │   ├── prerun/
│           │   │   ├── PreRunScreen.kt        ← 러닝 시작 전
│           │   │   └── PreRunViewModel.kt
│           │   ├── running/
│           │   │   ├── RunningScreen.kt       ← 러닝 중 메인
│           │   │   ├── AlarmOverlay.kt        ← 알람 오버레이 4종
│           │   │   └── RunningViewModel.kt
│           │   ├── postrun/
│           │   │   ├── PostRunScreen.kt       ← 러닝 후 결과
│           │   │   └── PostRunViewModel.kt
│           │   ├── goal/
│           │   │   ├── AiGoalScreen.kt        ← AI 다음 목표
│           │   │   └── AiGoalViewModel.kt
│           │   └── history/
│           │       ├── HistoryScreen.kt       ← 기록 히스토리
│           │       └── HistoryViewModel.kt
│           ├── sensor/
│           │   └── WearRunningService.kt      ← 실시간 센서 수집
│           └── alarm/
│               └── AlarmTriggerManager.kt     ← 알람 4종 트리거
│
└── app/                                ← 폰 앱 (백그라운드만)
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/runcoach/app/
            ├── RunCoachApp.kt           ← Hilt 초기화
            ├── alarm/
            │   └── WeeklyAlarmReceiver.kt   ← 매주 일요일 워치에 신호
            ├── data/db/
            │   ├── RunCoachDatabase.kt
            │   ├── RunningRecord.kt
            │   ├── RunningRecordDao.kt
            │   ├── WeeklyGoal.kt
            │   └── WeeklyGoalDao.kt
            ├── di/
            │   └── AppModule.kt
            ├── domain/
            │   └── AiGoalAdvisor.kt         ← AI 목표 계산
            ├── health/
            │   └── SamsungHealthManager.kt  ← Samsung Health SDK
            └── service/
                └── WearDataListenerService.kt ← 워치 데이터 수신 + DB 저장
```

---

## DB 구조

### running_records (폰 Room DB — 영구 저장)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK | |
| date | LONG | 러닝 날짜 |
| distanceKm | FLOAT | 실제 거리 |
| targetKm | FLOAT | 목표 거리 |
| avgPace | TEXT | 평균 페이스 (mm:ss) |
| avgHeartRate | INT | 평균 심박수 |
| maxHeartRate | INT | 최고 심박수 |
| durationSec | INT | 총 시간(초) |
| fatigueLevel | TEXT | low / mid / high |
| completed | BOOLEAN | 완주 여부 |

### weekly_goals (폰 Room DB — 영구 저장)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | INTEGER PK | |
| weekStart | LONG | 해당 주 시작일 |
| targetKm | FLOAT | 목표 거리 |
| targetPace | TEXT | 목표 페이스 |
| speedupKm | FLOAT | 스피드업 시작 구간 |
| speedupPct | INT | 스피드업 비율 (%) |
| hrAlertBpm | INT | 심박 경고 기준 |
| aiSuggested | BOOLEAN | AI 제안 여부 |

> 워치 내부에는 DataStore로 현재 목표 + 최근 기록만 캐시 저장

---

## MVP 구현 현황

### 완료 ✅
- [x] 프로젝트 구조 (Gradle, Manifest, libs.versions.toml)
- [x] Room DB 설계 및 구현 (Entity, DAO, Database)
- [x] Hilt 의존성 주입 설정
- [x] AI 목표 제안 로직 (AiGoalAdvisor)
- [x] 매주 일요일 알람 (WeeklyAlarmReceiver)
- [x] 폰 ↔ 워치 통신 (Wearable Data Layer)
- [x] 워치 진동 알람 4종

### 진행 중 🔄 (아키텍처 변경 반영)
- [ ] 워치 앱 전체 UI 전환 (Wear Compose 5개 화면)
- [ ] WearMainActivity + 워치 내비게이션 구성
- [ ] AlarmOverlay 컴포저블 (러닝 중 알람 4종)

### 남은 작업 🔲
- [ ] Samsung Health SDK 연동 (별도 다운로드 필요)
- [ ] WearRunningService — 실시간 센서 수집
- [ ] AlarmTriggerManager — 알람 조건 판단
- [ ] 워치 DataStore — 로컬 캐시
- [ ] 목표 직접 수정 화면 (워치)
- [ ] 첫 실행 온보딩 (워치 초기 목표 설정)
- [ ] 빌드 테스트 및 실기기 테스트

---

## 다음 단계 안내

### Android Studio에서 열기
1. Android Studio 실행
2. `File → Open → c:\BackUp\21th_Gpters\RunCoach` 선택
3. Gradle Sync 완료 후 빌드
4. **wear 모듈**을 갤럭시 워치5에 배포

### Samsung Health SDK 추가
1. [Samsung Developer](https://developer.samsung.com/health) 접속
2. Samsung Health SDK 다운로드
3. `app/libs/` 폴더에 `.aar` 파일 추가
4. `app/build.gradle.kts`에 `implementation(files("libs/samsung-health-sdk.aar"))` 추가

### Galaxy Watch 5 개발 환경
1. 워치에서 `설정 → 개발자 옵션` 활성화
2. ADB over Wi-Fi 또는 USB로 연결
3. Android Studio에서 wear 모듈 선택 후 배포
