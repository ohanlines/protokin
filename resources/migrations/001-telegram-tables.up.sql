create extension if not exists pgcrypto;

create table if not exists tg_updates (
  update_id bigint primary key,
  processed_at timestamptz not null default now()
);

create table if not exists tg_messages (
  id bigserial primary key,
  update_id bigint not null references tg_updates(update_id),
  chat_id bigint,
  user_id bigint,
  username text,
  first_name text,
  last_name text,
  message_text text,
  received_at timestamptz not null default now()
);

create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  telegram_user_id bigint not null unique,
  username text,
  first_name text,
  last_name text,
  default_currency char(3) not null default 'IDR',
  timezone text not null default 'Asia/Jakarta',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists user_crypto_profiles (
  user_id uuid primary key references users(id) on delete cascade,
  kdf_algo text not null,
  kdf_params jsonb not null,
  kdf_salt bytea not null,
  wrapped_dek bytea not null,
  dek_wrap_nonce bytea not null,
  dek_wrap_aad bytea,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists expenses (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  short_id text not null,
  expense_date date not null,
  ciphertext bytea not null,
  nonce bytea not null,
  aad bytea,
  source_message_id bigint,
  source_update_id bigint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
