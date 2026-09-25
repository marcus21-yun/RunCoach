-- =====================================================
-- Phase 5: 워치 직접 동기화용 테이블 (RLS 없음)
-- SupabaseSyncService가 사용하는 스키마와 일치
-- =====================================================

-- 1. 러닝 기록 (워치 직접 업로드)
create table if not exists run_records (
  id            uuid primary key default gen_random_uuid(),
  record_key    text unique not null,          -- 워치 기기 고유 ID (중복 방지)
  recorded_at   bigint not null,               -- epoch ms
  distance_km   float not null default 0,
  target_km     float not null default 0,
  avg_pace      text,
  target_pace   text,
  avg_heart_rate int,
  max_heart_rate int,
  duration_sec  int,
  fatigue_level text,
  completed     boolean default false,
  source        text,
  source_package text,
  created_at    timestamptz default now()
);

-- 2. 브리핑 기록 (사전/사후 AI 코칭 내용)
create table if not exists briefing_records (
  id            uuid primary key default gen_random_uuid(),
  briefing_key  text unique not null,
  record_key    text references run_records(record_key) on delete cascade,
  briefing_type text,                          -- 'pre' | 'post'
  content       text,
  created_at    bigint,
  provider      text,
  inserted_at   timestamptz default now()
);

-- 3. QR 공유 기록
create table if not exists shared_run_records (
  id            uuid primary key default gen_random_uuid(),
  share_token   text unique not null,
  share_url     text,
  recorded_at   bigint,
  distance_km   float,
  avg_pace      text,
  avg_heart_rate int,
  duration_sec  int,
  source        text,
  briefing      text,
  created_at    timestamptz default now()
);

-- RLS 비활성화 (워치 익명 쓰기 허용)
alter table run_records     disable row level security;
alter table briefing_records disable row level security;
alter table shared_run_records disable row level security;
