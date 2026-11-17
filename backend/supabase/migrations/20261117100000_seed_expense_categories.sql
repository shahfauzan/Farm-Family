-- Supply useful, family-scoped choices so a new expense does not depend on
-- a user creating a category before entering their first transaction.
create or replace function public.seed_default_expense_categories()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  insert into public.expense_categories(family_id, name, is_custom, created_by)
  select new.id, category_name, false, new.created_by
  from unnest(array[
    'Seeds', 'Fertilizer', 'Labor', 'Machinery', 'Irrigation',
    'Pesticides', 'Transport', 'Land rent', 'Repairs', 'Other'
  ]::text[]) as category_name
  on conflict (family_id, name) do nothing;
  return new;
end;
$$;
revoke all on function public.seed_default_expense_categories() from public, anon, authenticated;

drop trigger if exists family_default_expense_categories on public.families;
create trigger family_default_expense_categories
after insert on public.families
for each row execute function public.seed_default_expense_categories();

insert into public.expense_categories(family_id, name, is_custom, created_by)
select f.id, category_name, false, f.created_by
from public.families f
cross join unnest(array[
  'Seeds', 'Fertilizer', 'Labor', 'Machinery', 'Irrigation',
  'Pesticides', 'Transport', 'Land rent', 'Repairs', 'Other'
]::text[]) as category_name
on conflict (family_id, name) do nothing;

select pg_notify('pgrst', 'reload schema');
