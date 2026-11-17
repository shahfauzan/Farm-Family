-- Ensure freshly applied RPCs and tables become visible to the REST API immediately.
select pg_notify('pgrst', 'reload schema');
