create or replace function public.family_finance_summary(p_family uuid,p_from date default null,p_to date default null)
returns table(currency char(3),revenue numeric,cost numeric,profit numeric,cost_is_complete boolean)
language sql stable security invoker set search_path=public,pg_temp as $$
with revenue_by_currency as (
  select currency, sum(total_amount) total
  from public.sales
  where family_id = p_family and deleted_at is null
    and (p_from is null or sold_on >= p_from)
    and (p_to is null or sold_on <= p_to)
  group by currency
),
cost_by_currency as (
  select currency, sum(amount) total
  from public.expenses
  where family_id = p_family and deleted_at is null
    and (p_from is null or expense_date >= p_from)
    and (p_to is null or expense_date <= p_to)
  group by currency
),
season_complete as (
  select coalesce(bool_and(costs_complete and revenue_complete), false) complete
  from public.crop_seasons
  where family_id = p_family and deleted_at is null
    and (p_from is null or start_date >= p_from)
    and (p_to is null or start_date <= p_to)
),
totals as (
  select
    coalesce(r.currency, c.currency, 'NPR') currency,
    r.currency is not null as has_revenue,
    c.currency is not null as has_cost,
    r.total as revenue_total,
    c.total as cost_total
  from revenue_by_currency r
  full join cost_by_currency c using(currency)
)
select
  t.currency,
  case when t.has_revenue then t.revenue_total else null end as revenue,
  case when t.has_cost then t.cost_total else null end as cost,
  case
    when s.complete and t.has_revenue and t.has_cost and r.currency = c.currency then coalesce(r.total, 0) - coalesce(c.total, 0)
    else null
  end as profit,
  s.complete as cost_is_complete
from totals t
cross join season_complete s
left join revenue_by_currency r on r.currency = t.currency
left join cost_by_currency c on c.currency = t.currency
where public.is_family_member(p_family)
$$;
