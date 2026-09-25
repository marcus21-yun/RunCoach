create table if not exists public.run_records (
  record_key text primary key,
  recorded_at bigint not null,
  distance_km double precision not null,
  target_km double precision not null,
  avg_pace text not null,
  target_pace text not null,
  avg_heart_rate integer not null,
  max_heart_rate integer not null,
  duration_sec integer not null,
  fatigue_level text not null,
  completed boolean not null,
  source text not null,
  source_package text
);

create table if not exists public.briefing_records (
  briefing_key text primary key,
  record_key text not null references public.run_records(record_key) on delete cascade,
  briefing_type text not null,
  content text not null,
  created_at bigint not null,
  provider text not null
);

create table if not exists public.shared_run_records (
  share_token text primary key,
  share_url text not null,
  recorded_at bigint not null,
  distance_km double precision not null,
  avg_pace text not null,
  avg_heart_rate integer not null,
  duration_sec integer not null,
  source text not null,
  briefing text default ''
);

alter table public.run_records enable row level security;
alter table public.briefing_records enable row level security;
alter table public.shared_run_records enable row level security;

create policy "anon can upsert run records"
on public.run_records
for all
to anon
using (true)
with check (true);

create policy "anon can upsert briefing records"
on public.briefing_records
for all
to anon
using (true)
with check (true);

create policy "anon can read shared records"
on public.shared_run_records
for select
to anon
using (true);

create policy "anon can write shared records"
on public.shared_run_records
for insert
to anon
with check (true);
