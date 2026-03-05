create index if not exists idx_expenses_user_date
  on expenses (user_id, expense_date);

create unique index if not exists idx_expenses_user_short_id_unique
  on expenses (user_id, short_id)
  where deleted_at is null;

create unique index if not exists idx_expenses_source_update_unique
  on expenses (source_update_id)
  where source_update_id is not null;
