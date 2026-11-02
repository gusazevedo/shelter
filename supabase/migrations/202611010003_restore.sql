create function public.restore_record(p_kind text,p_id uuid) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform private.require_role(true);
 if p_kind='animal' then update animals set archived_at=null where id=p_id;
 elsif p_kind='adopter' then update adopters set archived_at=null where id=p_id;
 elsif p_kind='volunteer' then update volunteers set active=true,archived_at=null where id=p_id;
 else raise exception 'Tipo de cadastro inválido.';
 end if;
 if not found then raise exception 'Cadastro não encontrado.'; end if;
 perform private.audit(p_kind,p_id,'restore');
end $$;
revoke all on function public.restore_record(text,uuid) from public,anon;
grant execute on function public.restore_record(text,uuid) to authenticated;
