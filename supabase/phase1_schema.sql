-- ============================================================
-- RunCoach Phase 1 Schema — 50~60대 타겟 구독 모델
-- Supabase SQL Editor에서 실행
-- ============================================================

-- 1. 구독 정보 테이블
create table if not exists subscriptions (
  id           uuid primary key default gen_random_uuid(),
  user_id      text not null,
  billing_key  text not null,           -- 토스 빌링키
  customer_key text,                    -- 토스 customerKey
  status       text not null default 'ACTIVE', -- ACTIVE | PAUSED | CANCELLED
  started_at   timestamptz default now(),
  next_billing timestamptz,
  amount       integer not null default 6900,
  created_at   timestamptz default now()
);

-- user_id 인덱스 (구독 상태 조회)
create index if not exists subscriptions_user_id_idx on subscriptions(user_id);
create index if not exists subscriptions_status_idx on subscriptions(status);

-- 2. 운동 세션 리포트 테이블 (카카오톡 발송용)
create table if not exists run_reports (
  id           uuid primary key default gen_random_uuid(),
  user_id      text not null,
  session_date date not null,
  distance_m   integer,
  duration_sec integer,
  avg_hr       integer,
  max_hr       integer,
  hr_zone      text,                    -- SAFE | CAUTION | DANGER
  kakao_sent   boolean default false,
  created_at   timestamptz default now()
);

-- user_id + session_date 인덱스 (카카오 발송 쿼리)
create index if not exists run_reports_user_date_idx on run_reports(user_id, session_date desc);
create index if not exists run_reports_kakao_idx on run_reports(kakao_sent) where kakao_sent = false;

-- 3. HR 존 정의 참고 (50~60대 기준, age=55 예시)
-- SAFE    : bpm < maxHr * 0.65  (< 107bpm for age55)  → "안전 구간, 이 페이스 유지하세요"
-- CAUTION : bpm < maxHr * 0.78  (107~129bpm for age55) → "페이스 약간 줄이세요"
-- DANGER  : bpm >= maxHr * 0.78 (>= 129bpm for age55)  → "잠깐 걷기로 전환하세요!"
-- (maxHr = 220 - age)
