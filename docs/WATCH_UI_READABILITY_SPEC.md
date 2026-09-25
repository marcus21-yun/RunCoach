# RunCoach 워치 UI 가독성 스펙 (검증 완료)

> 대상: Galaxy Watch SM-R935N, 432×432, ~340dpi(≈203dp 폭 → 소형 둥근 <225dp)
> 근거: 현재 화면 실측 분석 + Wear OS 공식 가이드라인/WCAG 딥리서치 (2026-05-30)

## 타이포그래피 토큰 (최종)

| 토큰 | 크기/굵기 | 역할 | 용도 |
|------|----------|------|------|
| Display | 38sp Bold | display(고정) | 히어로 지표 1개 (러닝 중 = 현재 페이스) |
| MetricValue | 24sp Bold | **Numeral(tabular)** | 심박·거리·시간 등 변동 숫자 (자릿수 흔들림 방지) |
| Title | 20sp Bold | title(고정) | 화면 제목·D-day |
| Body | 16sp Regular | body | 카드 본문·메타데이터 |
| Label | 13sp Medium | label | 라벨("페이스","심박","거리") |
| Caption | 12sp Regular | caption | 단위·필수 보조(하한) |

- **12sp 미만 전면 제거** (현재 9~11sp 46곳 → 13sp↑로 상향)
- 본문 light 굵기 금지, 핵심 숫자 Bold/Medium
- Display·Numeral은 사용자 글자크기 스케일링 비적용(고정) → 레이아웃 붕괴 방지

## 색상 토큰 (WCAG 검증)

| 토큰 | hex | 용도 | 대비 기준 |
|------|-----|------|----------|
| Background | `#0F1115` | 배경(순흑 대신 — 눈피로·할레이션 완화) | — |
| TextPrimary | `#FFFFFF` | 핵심 대형 숫자 | 대형 ≥3:1 |
| TextBody | `#E9ECF1` | 본문 흰색(순백 할레이션 완화) | ≥4.5:1 |
| TextSecondary | `#B8C2D0` | 라벨·보조 (기존 #888888 대체) | ≥4.5:1 |
| Accent | `#4FC3F7` | 브랜드·강조 | 그래픽 ≥3:1 |
| Success | `#00E676` | 목표보다 빠름·완주 | 배지/아이콘용 ≥3:1 |
| Warning | `#FFB74D` | GPS 탐색·주의 | 배지/아이콘용(텍스트 금지) |
| Danger | `#FF5252` | 심박 경고·느림 | 배지/아이콘용(텍스트 금지) |

- **상태 색은 작은 텍스트로 쓰지 말 것** (Warning/Danger 4.5:1 미달). 배지·게이지·아이콘으로.
- **색만으로 의미 전달 금지** → 색 + 아이콘/텍스트 병행 (예: 🔴 + "느림")
- 야외 직사광 대비: 가능하면 AAA(일반 7:1, 대형 4.5:1) 지향, 굵은 획+고대비

## 레이아웃 (둥근 소형 화면)

- 핵심 1~2개 지표만 한 화면에 (글랜서빌리티 5초 원칙)
- 리스트: `TransformingLazyColumn` + Horologist `rememberResponsiveColumnPadding`(가장자리 페이드)
- 마진은 **%기반**, 둥근 안전영역 인셋 ~14.6%(0.146467) → 모서리 잘림 방지
- 터치 타깃 ≥48×48dp
- 상단 `TimeText`(곡선) 항상 표시, 스크롤 시 `scrollAway()`
- 운동 중 스크롤/탭 최소화

## 러닝 중 화면 위계 (큰→작은)

1. **현재 페이스** — Display 38sp 중앙 히어로 (코칭 앱의 1차 피드백 대상)
2. **심박·거리·경과시간** — MetricValue 24sp Numeral, 보조 배치
3. **라벨** — Label 13sp #B8C2D0
4. **상태(심박존/목표대비)** — 색+아이콘 게이지/배지
5. 케이던스 등 심화지표 → 별도 스와이프 화면 분리
- 시각 + 실시간 음성 피드백 병행(현 TTS 코칭 유지)

## 구현 방식

- `ui/theme/RcType.kt`(타입), `ui/theme/RcColors.kt`(색) 토큰 파일 신설
- 6개 화면을 토큰 참조로 리팩터 → 한 곳에서 전체 제어

## 핵심 출처
- Wear 타이포그래피: https://developer.android.com/design/ui/wear/guides/styles/typography
- Wear 앱 품질(WO-V14 폰트/WO-V2 터치): https://developer.android.com/docs/quality-guidelines/wear-app-quality
- Compose 화면크기/리스트: https://developer.android.com/training/wearables/compose/screen-size
- WCAG 1.4.3/1.4.11 대비: https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html
- 화면 형태(둥근 안전영역): https://developer.android.com/design/ui/wear/guides/foundations/screen-shapes
