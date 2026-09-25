-- ============================================================
-- RunCoach Phase 4 — RLS (Row Level Security) 보안 정책
-- Supabase SQL Editor에서 실행 (phase1_schema.sql 실행 후)
-- ============================================================

-- ──────────────────────────────────────────────────────────────
-- 1. payments 테이블 RLS
-- ──────────────────────────────────────────────────────────────
alter table payments enable row level security;

-- 서버(SERVICE_ROLE)만 INSERT/UPDATE 가능
-- 일반 사용자는 SELECT 불가 (payments에는 user_id가 없으므로 전면 차단)
-- 토스 웹훅과 서버 API는 service_role 키를 사용하므로 RLS 우회
create policy "payments: service_role only"
  on payments
  for all
  using (false);   -- anon/authenticated 역할은 전면 차단

-- ──────────────────────────────────────────────────────────────
-- 2. subscriptions 테이블 RLS
-- ──────────────────────────────────────────────────────────────
alter table subscriptions enable row level security;

-- 본인 구독만 SELECT 허용 (billing_key 컬럼은 별도 제한)
create policy "subscriptions: read own"
  on subscriptions
  for select
  using (auth.uid()::text = user_id);

-- INSERT/UPDATE/DELETE는 서버(service_role)만 가능
create policy "subscriptions: service_role write"
  on subscriptions
  for all
  using (false);  -- anon/authenticated 쓰기 전면 차단

-- ──────────────────────────────────────────────────────────────
-- 3. billing_key 컬럼 마스킹 (Column Security)
-- subscriptions SELECT 시 billing_key는 보이지 않도록 뷰로 래핑
-- ──────────────────────────────────────────────────────────────
create or replace view subscriptions_safe as
  select
    id,
    user_id,
    status,
    started_at,
    next_billing,
    amount,
    created_at
    -- billing_key, customer_key 제외
  from subscriptions;

-- 사용자는 뷰를 통해서만 구독 상태 확인 (billing_key 노출 없음)
grant select on subscriptions_safe to authenticated;

-- ──────────────────────────────────────────────────────────────
-- 4. run_reports 테이블 RLS
-- ──────────────────────────────────────────────────────────────
alter table run_reports enable row level security;

create policy "run_reports: read own"
  on run_reports
  for select
  using (auth.uid()::text = user_id);

create policy "run_reports: service_role write"
  on run_reports
  for all
  using (false);

-- ──────────────────────────────────────────────────────────────
-- 5. user_profiles 테이블 (Step 2와 함께 생성)
-- ──────────────────────────────────────────────────────────────
create table if not exists user_profiles (
  id           uuid primary key default gen_random_uuid(),
  user_id      text not null unique,  -- Supabase Auth uid
  birth_year   integer,               -- 나이 계산용 (생년도)
  guardian_phone text,                -- 보호자(자녀) 카카오 연락처
  kakao_token  text,                  -- 카카오 액세스 토큰 (리포트 발송용)
  created_at   timestamptz default now(),
  updated_at   timestamptz default now()
);

alter table user_profiles enable row level security;

-- 본인 프로필만 읽기/쓰기 허용
create policy "user_profiles: read own"
  on user_profiles
  for select
  using (auth.uid()::text = user_id);

create policy "user_profiles: write own"
  on user_profiles
  for insert
  with check (auth.uid()::text = user_id);

create policy "user_profiles: update own"
  on user_profiles
  for update
  using (auth.uid()::text = user_id);

-- ──────────────────────────────────────────────────────────────
-- 확인 쿼리 (실행 후 검증용)
-- ──────────────────────────────────────────────────────────────
-- select tablename, rowsecurity from pg_tables
--   where schemaname = 'public'
--   and tablename in ('payments','subscriptions','run_reports','user_profiles');
-- → rowsecurity = true 이면 RLS 활성화 완료
