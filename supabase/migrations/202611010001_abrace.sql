-- Abrace: all mutations go through checked, transactional RPCs.
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to authenticated;

create table public.bases (
 id uuid primary key default gen_random_uuid(), name text not null check(length(trim(name)) > 1)
);
insert into public.bases(id,name) values
 ('00000000-0000-0000-0000-000000000001','Abrigo Abrace'),
 ('00000000-0000-0000-0000-000000000002','Casa de apoio');

create table public.volunteers (
 id uuid primary key references auth.users(id), full_name text not null,
 email text not null unique, phone text not null default '',
 role text not null default 'viewer' check(role in ('viewer','editor','admin')),
 active boolean not null default false, created_at timestamptz not null default now(),
 archived_at timestamptz
);
create table public.invitation_allowlist (
 email text primary key, full_name text not null, phone text not null default '',
 invited_by uuid not null references public.volunteers(id), created_at timestamptz not null default now()
);
create function private.on_auth_user() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare invitation invitation_allowlist;
begin
 select * into invitation from invitation_allowlist where email=lower(new.email);
 insert into volunteers(id,email,full_name,phone,active)
 values(new.id,lower(new.email),coalesce(invitation.full_name,'Aguardando convite'),coalesce(invitation.phone,''),invitation.email is not null);
 return new;
end $$;
create trigger abrace_auth_user after insert on auth.users for each row execute function private.on_auth_user();

create function private.current_role() returns text language sql stable security definer set search_path=public,pg_temp as $$
 select role from volunteers where id=auth.uid() and active and archived_at is null
$$;
create function private.can_read() returns boolean language sql stable security definer set search_path=public,pg_temp as $$
 select coalesce(private.current_role() in ('viewer','editor','admin'),false)
$$;
create function private.require_role(p_admin boolean default false) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 if (p_admin and coalesce(private.current_role(),'') <> 'admin') or
    (not p_admin and coalesce(private.current_role(),'') not in ('editor','admin')) then
  raise exception 'Você não tem permissão para esta ação.' using errcode='42501';
 end if;
end $$;

create table public.animals (
 id uuid primary key default gen_random_uuid(), name text not null check(length(trim(name))>0),
 species text not null check(species in ('dog','cat')), sex text not null default 'unknown' check(sex in ('unknown','female','male')),
 estimated_birth_date date, breed text not null default '', weight_kg numeric check(weight_kg>0),
 color text not null default '', microchip text not null default '', admitted_on date not null default current_date,
 home_base_id uuid not null references bases(id), status text not null default 'available'
 check(status in ('available','unavailable','assessment','adopted','deceased')),
 neutered text not null default 'unknown' check(neutered in ('yes','no','unknown')),
 rabies_vaccinated text not null default 'unknown' check(rabies_vaccinated in ('yes','no','unknown')),
 leishmaniasis text not null default 'unknown' check(leishmaniasis in ('yes','no','unknown')),
 health_notes text not null default '', notes text not null default '',
 photo_paths text[] not null default '{}' check(cardinality(photo_paths)<=3),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(), archived_at timestamptz
);
create table public.base_transfers (
 id uuid primary key default gen_random_uuid(), animal_id uuid not null references animals(id),
 from_base_id uuid references bases(id), to_base_id uuid not null references bases(id),
 happened_at timestamptz not null default now(), actor_id uuid not null references volunteers(id)
);
create table public.adopters (
 id uuid primary key default gen_random_uuid(), full_name text not null check(length(trim(full_name))>2),
 birth_date date not null, cpf text not null unique check(cpf ~ '^\d{11}$'),
 phone text not null check(length(regexp_replace(phone,'\D','','g'))>=10), email text not null,
 street text not null check(length(trim(street))>0), number text not null check(length(trim(number))>0),
 complement text not null default '', neighborhood text not null check(length(trim(neighborhood))>0),
 city text not null check(length(trim(city))>0), state text not null check(state ~ '^[A-Z]{2}$'),
 postal_code text not null check(postal_code ~ '^\d{8}$'), has_other_animals boolean not null default false,
 other_animals text not null default '', notes text not null default '', created_at timestamptz not null default now(), archived_at timestamptz
);
create table public.adoptions (
 id uuid primary key default gen_random_uuid(), animal_id uuid not null references animals(id),
 adopter_id uuid not null references adopters(id), origin_base_id uuid not null references bases(id),
 adopted_on date not null default current_date, created_by uuid not null references volunteers(id),
 followup_status text not null default 'ongoing' check(followup_status in ('ongoing','adopted','recollected')),
 followup_closed_at timestamptz, ended_at timestamptz, end_reason text check(end_reason in ('returned','deceased')),
 return_reason text not null default '', notes text not null default '', created_at timestamptz not null default now(),
 check((ended_at is null)=(end_reason is null))
);
create unique index one_current_placement on adoptions(animal_id) where ended_at is null;
create index adoptions_adopter on adoptions(adopter_id);
create table public.visits (
 id uuid primary key default gen_random_uuid(), adopter_id uuid not null references adopters(id),
 visitor_id uuid not null references volunteers(id), scheduled_at timestamptz not null,
 visited_at timestamptz, cancelled_at timestamptz, notes text not null default '', created_by uuid not null references volunteers(id)
);
create table public.visit_animals (
 visit_id uuid not null references visits(id), adoption_id uuid not null references adoptions(id),
 outcome text check(outcome in ('well_cared','needs_attention')), notes text not null default '',
 primary key(visit_id,adoption_id)
);
create index visit_animals_adoption on visit_animals(adoption_id);
create table public.vaccinations (
 id uuid primary key default gen_random_uuid(), animal_id uuid not null references animals(id),
 name text not null check(length(trim(name))>0), administered_on date, scheduled_on date,
 next_due_on date, notes text not null default '', created_by uuid not null references volunteers(id),
 created_at timestamptz not null default now(), archived_at timestamptz,
 check(administered_on is not null or scheduled_on is not null),
 check(next_due_on is null or next_due_on>administered_on)
);
create table public.treatments (
 id uuid primary key default gen_random_uuid(), animal_id uuid not null references animals(id),
 name text not null check(length(trim(name))>0), instructions text not null default '',
 start_date date not null, end_date date, total_doses integer check(total_doses between 1 and 1000),
 schedule_type text not null check(schedule_type in ('daily','weekly','hourly')),
 time_of_day time not null, interval_hours integer check(interval_hours between 1 and 720),
 weekdays integer[] not null default '{}', timezone text not null default 'America/Fortaleza',
 status text not null default 'active' check(status in ('active','suspended','completed')),
 created_by uuid not null references volunteers(id), created_at timestamptz not null default now(), archived_at timestamptz,
 check((end_date is not null)::integer+(total_doses is not null)::integer=1),
 check(end_date is null or end_date>=start_date),
 check(schedule_type<>'hourly' or interval_hours is not null),
 check(schedule_type<>'weekly' or cardinality(weekdays)>0), check(weekdays <@ array[1,2,3,4,5,6,7])
);
create table public.treatment_doses (
 id uuid primary key default gen_random_uuid(), treatment_id uuid not null references treatments(id),
 scheduled_at timestamptz not null, given_at timestamptz, given_by uuid references volunteers(id),
 notes text not null default '', unique(treatment_id,scheduled_at)
);
create index doses_pending on treatment_doses(treatment_id,scheduled_at) where given_at is null;
create table public.audit_events (
 id bigint generated always as identity primary key, actor_id uuid references volunteers(id),
 entity_type text not null, entity_id uuid not null, action text not null, occurred_at timestamptz not null default now()
);
create function private.audit(p_type text,p_id uuid,p_action text) returns void language sql security definer set search_path=public,pg_temp as $$
 insert into audit_events(actor_id,entity_type,entity_id,action) values(auth.uid(),p_type,p_id,p_action)
$$;
create function private.valid_cpf(p_cpf text) returns boolean language plpgsql immutable as $$
declare i integer; s integer; digit integer;
begin
 if p_cpf !~ '^\d{11}$' or p_cpf=repeat(substr(p_cpf,1,1),11) then return false; end if;
 for i in 10..11 loop
  select sum(substr(p_cpf,n,1)::int*(i+1-n)) into s from generate_series(1,i-1) n;
  digit := (s*10)%11; if digit=10 then digit:=0; end if;
  if digit<>substr(p_cpf,i,1)::int then return false; end if;
 end loop;
 return true;
end $$;

create function public.save_record(p_kind text,p_data jsonb) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid := coalesce(nullif(p_data->>'id','')::uuid,gen_random_uuid());
 a animals; old_a animals; person adopters; vac vaccinations; tr treatments;
 bound_date date; series_end timestamptz; total integer;
begin
 perform private.require_role();
 if p_kind='animal' then
  select * into old_a from animals where id=v_id for update;
  if old_a.archived_at is not null then raise exception 'Este animal está arquivado.'; end if;
  a:=jsonb_populate_record(null::animals,p_data || jsonb_build_object('id',v_id));
  if a.estimated_birth_date>current_date or a.admitted_on>current_date then raise exception 'Informe datas que não estejam no futuro.'; end if;
  if exists(select 1 from adoptions where animal_id=v_id and ended_at is null) then
   if a.status not in ('adopted','deceased') or a.home_base_id<>old_a.home_base_id then
    raise exception 'Registre o recolhimento antes de mudar a disponibilidade ou a base.';
   end if;
  elsif a.status='adopted' then raise exception 'Use o registro de adoção para vincular o adotante.';
  end if;
  if exists(select 1 from unnest(a.photo_paths) path where path not like v_id::text || '/%') then raise exception 'Foto inválida para este animal.'; end if;
  insert into animals(id,name,species,sex,estimated_birth_date,breed,weight_kg,color,microchip,admitted_on,home_base_id,status,neutered,rabies_vaccinated,leishmaniasis,health_notes,notes,photo_paths)
  values(v_id,trim(a.name),a.species,a.sex,a.estimated_birth_date,coalesce(a.breed,''),a.weight_kg,coalesce(a.color,''),coalesce(a.microchip,''),a.admitted_on,a.home_base_id,a.status,a.neutered,a.rabies_vaccinated,a.leishmaniasis,coalesce(a.health_notes,''),coalesce(a.notes,''),coalesce(a.photo_paths,'{}'))
  on conflict(id) do update set name=excluded.name,species=excluded.species,sex=excluded.sex,estimated_birth_date=excluded.estimated_birth_date,breed=excluded.breed,weight_kg=excluded.weight_kg,color=excluded.color,microchip=excluded.microchip,admitted_on=excluded.admitted_on,home_base_id=excluded.home_base_id,status=excluded.status,neutered=excluded.neutered,rabies_vaccinated=excluded.rabies_vaccinated,leishmaniasis=excluded.leishmaniasis,health_notes=excluded.health_notes,notes=excluded.notes,photo_paths=excluded.photo_paths,updated_at=now();
  if old_a.home_base_id is distinct from a.home_base_id then
   insert into base_transfers(animal_id,from_base_id,to_base_id,actor_id) values(v_id,old_a.home_base_id,a.home_base_id,auth.uid());
  end if;
  if a.status='deceased' then
   update adoptions set ended_at=now(),end_reason='deceased' where animal_id=v_id and ended_at is null;
   update visits set cancelled_at=now() where visited_at is null and cancelled_at is null and id in
    (select va.visit_id from visit_animals va join adoptions ad on ad.id=va.adoption_id where ad.animal_id=v_id)
    and not exists(select 1 from visit_animals va join adoptions ad on ad.id=va.adoption_id where va.visit_id=visits.id and ad.ended_at is null and ad.followup_status='ongoing');
  end if;
 elsif p_kind='adopter' then
  person:=jsonb_populate_record(null::adopters,p_data || jsonb_build_object('id',v_id));
  if not private.valid_cpf(person.cpf) then raise exception 'CPF inválido.'; end if;
  if person.birth_date>current_date then raise exception 'Data de nascimento inválida.'; end if;
  if person.email !~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$' then raise exception 'E-mail inválido.'; end if;
  if exists(select 1 from adopters where id=v_id and archived_at is not null) then raise exception 'Adotante arquivado.'; end if;
  insert into adopters(id,full_name,birth_date,cpf,phone,email,street,number,complement,neighborhood,city,state,postal_code,has_other_animals,other_animals,notes)
  values(v_id,trim(person.full_name),person.birth_date,person.cpf,person.phone,lower(person.email),person.street,person.number,coalesce(person.complement,''),person.neighborhood,person.city,person.state,person.postal_code,person.has_other_animals,coalesce(person.other_animals,''),coalesce(person.notes,''))
  on conflict(id) do update set full_name=excluded.full_name,birth_date=excluded.birth_date,cpf=excluded.cpf,phone=excluded.phone,email=excluded.email,street=excluded.street,number=excluded.number,complement=excluded.complement,neighborhood=excluded.neighborhood,city=excluded.city,state=excluded.state,postal_code=excluded.postal_code,has_other_animals=excluded.has_other_animals,other_animals=excluded.other_animals,notes=excluded.notes;
 elsif p_kind='vaccination' then
  vac:=jsonb_populate_record(null::vaccinations,p_data || jsonb_build_object('id',v_id));
  if not exists(select 1 from animals where id=vac.animal_id and archived_at is null and status<>'deceased') then raise exception 'Animal não disponível para este registro.'; end if;
  if vac.administered_on>current_date then raise exception 'A data da aplicação não pode estar no futuro.'; end if;
  insert into vaccinations(id,animal_id,name,administered_on,scheduled_on,next_due_on,notes,created_by)
  values(v_id,vac.animal_id,trim(vac.name),vac.administered_on,vac.scheduled_on,vac.next_due_on,coalesce(vac.notes,''),auth.uid())
  on conflict(id) do update set name=excluded.name,administered_on=excluded.administered_on,scheduled_on=coalesce(excluded.scheduled_on,vaccinations.scheduled_on),next_due_on=excluded.next_due_on,notes=excluded.notes
  where vaccinations.administered_on is null and vaccinations.animal_id=excluded.animal_id;
  if not found then raise exception 'Esta vacina já possui uma aplicação registrada.'; end if;
 elsif p_kind='treatment' then
  tr:=jsonb_populate_record(null::treatments,p_data || jsonb_build_object('id',v_id));
  if not exists(select 1 from animals where id=tr.animal_id and archived_at is null and status<>'deceased') then raise exception 'Animal não disponível para este registro.'; end if;
  bound_date:=coalesce(tr.end_date,tr.start_date+(tr.total_doses*7+7));
  if bound_date<tr.start_date or (tr.end_date is not null and bound_date-tr.start_date>366) then raise exception 'Informe um tratamento com até 366 dias ou até 1000 aplicações.'; end if;
  insert into treatments(id,animal_id,name,instructions,start_date,end_date,total_doses,schedule_type,time_of_day,interval_hours,weekdays,timezone,created_by)
  values(v_id,tr.animal_id,trim(tr.name),coalesce(tr.instructions,''),tr.start_date,tr.end_date,tr.total_doses,tr.schedule_type,tr.time_of_day,tr.interval_hours,coalesce(tr.weekdays,'{}'),coalesce(tr.timezone,'America/Fortaleza'),auth.uid());
  if tr.schedule_type='hourly' then
   series_end:=case when tr.end_date is not null then (tr.end_date+time '23:59:59') at time zone coalesce(tr.timezone,'America/Fortaleza') else ((tr.start_date+tr.time_of_day) at time zone coalesce(tr.timezone,'America/Fortaleza')) + make_interval(hours=>tr.interval_hours*(tr.total_doses-1)) end;
   insert into treatment_doses(treatment_id,scheduled_at)
   select v_id,d from generate_series((tr.start_date+tr.time_of_day) at time zone coalesce(tr.timezone,'America/Fortaleza'),series_end,make_interval(hours=>tr.interval_hours)) d limit 1001;
  else
   insert into treatment_doses(treatment_id,scheduled_at)
   select v_id,(d::date+tr.time_of_day) at time zone coalesce(tr.timezone,'America/Fortaleza')
   from generate_series(tr.start_date::timestamp,bound_date::timestamp,interval '1 day') d
   where tr.schedule_type='daily' or extract(isodow from d)::int=any(tr.weekdays)
   order by d limit coalesce(tr.total_doses,1001);
  end if;
  select count(*) into total from treatment_doses where treatment_id=v_id;
  if total=0 or total>1000 then raise exception 'O calendário precisa conter entre 1 e 1000 aplicações.'; end if;
 elsif p_kind='volunteer' then
  perform private.require_role(true);
  update volunteers set full_name=trim(p_data->>'full_name'),phone=coalesce(p_data->>'phone','') where id=v_id;
  if not found then raise exception 'Voluntário não encontrado.'; end if;
 else raise exception 'Tipo de cadastro inválido.';
 end if;
 perform private.audit(p_kind,v_id,'save');
 return v_id;
end $$;

create function public.create_adoption(p_data jsonb) returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare a animals; person_id uuid:=(p_data->>'adopter_id')::uuid; day date:=(p_data->>'adopted_on')::date; ids uuid[];
begin
 perform private.require_role();
 select array_agg(value::uuid) into ids from jsonb_array_elements_text(p_data->'animal_ids');
 if coalesce(cardinality(ids),0)=0 or cardinality(ids)<>(select count(distinct x) from unnest(ids) x) then raise exception 'Selecione os animais sem repetir.'; end if;
 if not exists(select 1 from adopters where id=person_id and archived_at is null) then raise exception 'Adotante não encontrado.'; end if;
 if day is null or day>current_date then raise exception 'Data de adoção inválida.'; end if;
 perform 1 from animals where id=any(ids) order by id for update;
 if (select count(*) from animals where id=any(ids) and status='available' and archived_at is null)<>cardinality(ids) then raise exception 'Um dos animais não está disponível para adoção.'; end if;
 for a in select * from animals where id=any(ids) order by id loop
  insert into adoptions(animal_id,adopter_id,origin_base_id,adopted_on,created_by,notes) values(a.id,person_id,a.home_base_id,day,auth.uid(),coalesce(p_data->>'notes',''));
  update animals set status='adopted',updated_at=now() where id=a.id;
  perform private.audit('animal',a.id,'adopt');
 end loop;
end $$;

create function public.schedule_visit(p_data jsonb) returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare v_id uuid:=gen_random_uuid(); ids uuid[]; ad adoptions; person_id uuid; visitor uuid:=(p_data->>'visitor_id')::uuid;
begin
 perform private.require_role();
 if not exists(select 1 from volunteers where id=visitor and active and role in ('editor','admin') and archived_at is null) then raise exception 'O visitante precisa de permissão de edição.'; end if;
 select array_agg(value::uuid) into ids from jsonb_array_elements_text(p_data->'adoption_ids');
 if coalesce(cardinality(ids),0)=0 or cardinality(ids)<>(select count(distinct x) from unnest(ids) x) then raise exception 'Selecione os animais da visita.'; end if;
 perform 1 from adoptions where id=any(ids) order by id for update;
 if (select count(*) from adoptions where id=any(ids) and ended_at is null and followup_status='ongoing')<>cardinality(ids) then raise exception 'Acompanhamento encerrado ou não encontrado.'; end if;
 if (select count(distinct adopter_id) from adoptions where id=any(ids))<>1 then raise exception 'Uma visita só pode reunir animais do mesmo adotante.'; end if;
 for ad in select * from adoptions where id=any(ids) loop
  person_id:=ad.adopter_id;
  if (select count(*) from visit_animals va join visits v on v.id=va.visit_id where va.adoption_id=ad.id and v.cancelled_at is null)>=3 then raise exception 'Este animal já possui três visitas realizadas ou agendadas.'; end if;
  if (p_data->>'scheduled_at')::timestamptz::date<ad.adopted_on then raise exception 'A visita deve acontecer após a adoção.'; end if;
 end loop;
 insert into visits(id,adopter_id,visitor_id,scheduled_at,notes,created_by) values(v_id,person_id,visitor,(p_data->>'scheduled_at')::timestamptz,coalesce(p_data->>'notes',''),auth.uid());
 insert into visit_animals(visit_id,adoption_id) select v_id,unnest(ids);
 perform private.audit('visit',v_id,'schedule');
 return v_id;
end $$;

create function public.record_visit(p_data jsonb) returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare v visits; result jsonb; actual_time timestamptz:=coalesce((p_data->>'visited_at')::timestamptz,now()); expected integer;
begin
 perform private.require_role();
 select * into v from visits where id=(p_data->>'id')::uuid for update;
 if v.id is null or v.cancelled_at is not null or v.visited_at is not null then raise exception 'Visita já realizada, cancelada ou não encontrada.'; end if;
 if v.visitor_id<>auth.uid() and private.current_role()<>'admin' then raise exception 'Somente o visitante designado ou um administrador pode registrar esta visita.'; end if;
 if actual_time>now()+interval '1 minute' then raise exception 'A realização não pode estar no futuro.'; end if;
 perform 1 from adoptions where id in(select adoption_id from visit_animals where visit_id=v.id) order by id for update;
 select count(*) into expected from visit_animals va join adoptions a on a.id=va.adoption_id where va.visit_id=v.id and a.ended_at is null and a.followup_status='ongoing';
 if expected=0 then raise exception 'Todos os acompanhamentos desta visita foram encerrados.'; end if;
 if jsonb_array_length(p_data->'results')<>expected then raise exception 'Informe um resultado para cada animal em acompanhamento.'; end if;
 if (select count(distinct x->>'adoption_id') from jsonb_array_elements(p_data->'results') x)<>expected then raise exception 'Não repita animais nos resultados.'; end if;
 for result in select * from jsonb_array_elements(p_data->'results') loop
  if not exists(select 1 from visit_animals va join adoptions a on a.id=va.adoption_id where va.visit_id=v.id and a.id=(result->>'adoption_id')::uuid and a.ended_at is null and a.followup_status='ongoing') then raise exception 'Animal não pertence a esta visita ou já foi recolhido.'; end if;
  if actual_time::date<(select adopted_on from adoptions where id=(result->>'adoption_id')::uuid) then raise exception 'A realização deve acontecer após a adoção.'; end if;
  update visit_animals set outcome=result->>'outcome',notes=coalesce(result->>'notes','') where visit_id=v.id and adoption_id=(result->>'adoption_id')::uuid;
  if result->>'outcome' is null then raise exception 'Informe a avaliação de cada animal.'; end if;
 end loop;
 update visits set visited_at=actual_time,notes=coalesce(p_data->>'notes',notes) where id=v.id;
 perform private.audit('visit',v.id,'complete');
end $$;

create function public.cancel_visit(p_id uuid) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform private.require_role();
 update visits set cancelled_at=now() where id=p_id and visited_at is null and cancelled_at is null;
 if not found then raise exception 'Esta visita não pode ser cancelada.'; end if;
 perform private.audit('visit',p_id,'cancel');
end $$;

create function public.close_followup(p_id uuid,p_outcome text,p_reason text default '') returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare ad adoptions; animal uuid; visitor uuid;
begin
 perform private.require_role();
 select animal_id into animal from adoptions where id=p_id;
 perform 1 from animals where id=animal for update;
 select * into ad from adoptions where id=p_id for update;
 if ad.id is null or ad.ended_at is not null then raise exception 'Adoção encerrada ou não encontrada.'; end if;
 select v.visitor_id into visitor from visits v join visit_animals va on va.visit_id=v.id where va.adoption_id=p_id and va.outcome is not null and v.visited_at is not null order by v.visited_at desc limit 1;
 if private.current_role()<>'admin' and (visitor is null or visitor<>auth.uid()) then raise exception 'O encerramento deve ser registrado pelo visitante ou por um administrador.'; end if;
 if p_outcome='adopted' then
  if ad.followup_status<>'ongoing' then raise exception 'Acompanhamento já encerrado.'; end if;
  if (select count(*) from visit_animals va join visits v on v.id=va.visit_id where va.adoption_id=p_id and va.outcome is not null and v.visited_at is not null and v.cancelled_at is null)<>3 then raise exception 'Conclua as três visitas antes de encerrar como Adotado.'; end if;
  update adoptions set followup_status='adopted',followup_closed_at=now() where id=p_id;
 elsif p_outcome='recollected' then
  if length(trim(p_reason))=0 then raise exception 'Descreva o motivo do retorno à base.'; end if;
  update adoptions set followup_status='recollected',followup_closed_at=coalesce(followup_closed_at,now()),ended_at=now(),end_reason='returned',return_reason=trim(p_reason) where id=p_id;
  update animals set status='assessment',home_base_id=ad.origin_base_id,updated_at=now() where id=ad.animal_id;
 else raise exception 'Escolha Adotado ou Recolhido.';
 end if;
 update visits set cancelled_at=now() where visited_at is null and cancelled_at is null and id in(select visit_id from visit_animals where adoption_id=p_id)
 and not exists(select 1 from visit_animals va join adoptions other_ad on other_ad.id=va.adoption_id where va.visit_id=visits.id and other_ad.ended_at is null and other_ad.followup_status='ongoing');
 perform private.audit('adoption',p_id,p_outcome);
end $$;

create function public.record_dose(p_id uuid,p_given_at timestamptz default now(),p_notes text default '') returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare tr_id uuid;
begin
 perform private.require_role();
 if p_given_at>now()+interval '1 minute' then raise exception 'A aplicação não pode estar no futuro.'; end if;
 select treatment_id into tr_id from treatment_doses where id=p_id;
 perform 1 from treatments where id=tr_id and status='active' and archived_at is null and exists(select 1 from animals where id=treatments.animal_id and archived_at is null and status<>'deceased') for update;
 if not found then raise exception 'Tratamento não está ativo.'; end if;
 update treatment_doses set given_at=p_given_at,given_by=auth.uid(),notes=p_notes where id=p_id and given_at is null;
 if not found then raise exception 'Aplicação já registrada ou não encontrada.'; end if;
 if not exists(select 1 from treatment_doses where treatment_id=tr_id and given_at is null) then update treatments set status='completed' where id=tr_id; end if;
 perform private.audit('dose',p_id,'give');
end $$;
create function public.suspend_treatment(p_id uuid) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform private.require_role();
 update treatments set status='suspended' where id=p_id and status='active';
 if not found then raise exception 'Tratamento não está ativo.'; end if;
 perform private.audit('treatment',p_id,'suspend');
end $$;

create function public.set_volunteer_role(p_id uuid,p_role text) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform private.require_role(true);
 perform pg_advisory_xact_lock(8246271);
 if p_role not in ('viewer','editor','admin') then raise exception 'Permissão inválida.'; end if;
 if exists(select 1 from volunteers where id=p_id and active and role='admin') and p_role<>'admin' and
 (select count(*) from volunteers where active and role='admin' and archived_at is null)<=1 then raise exception 'Mantenha ao menos um administrador ativo.'; end if;
 update volunteers set role=p_role where id=p_id and active and archived_at is null;
 if not found then raise exception 'Voluntário não está ativo.'; end if;
 perform private.audit('volunteer',p_id,'role:'||p_role);
end $$;

create function public.archive_record(p_kind text,p_id uuid,p_permanent boolean default false) returns void language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform private.require_role(true);
 if p_kind='animal' then
  perform 1 from animals where id=p_id for update;
  if exists(select 1 from adoptions where animal_id=p_id and ended_at is null) then raise exception 'O animal ainda está em uma adoção vigente.'; end if;
  if p_permanent then
   if exists(select 1 from adoptions where animal_id=p_id) or exists(select 1 from vaccinations where animal_id=p_id) or exists(select 1 from treatments where animal_id=p_id) or exists(select 1 from base_transfers where animal_id=p_id and from_base_id is not null) then raise exception 'Este animal possui histórico. Use arquivar.'; end if;
   delete from base_transfers where animal_id=p_id;
   delete from animals where id=p_id;
  else update animals set archived_at=now() where id=p_id; end if;
 elsif p_kind='adopter' then
  if exists(select 1 from adoptions where adopter_id=p_id and ended_at is null) then raise exception 'O adotante ainda possui uma adoção vigente.'; end if;
  if p_permanent then
   if exists(select 1 from adoptions where adopter_id=p_id) then raise exception 'Este adotante possui histórico. Use arquivar.'; end if;
   delete from adopters where id=p_id;
  else update adopters set archived_at=now() where id=p_id; end if;
 elsif p_kind='volunteer' then
  perform pg_advisory_xact_lock(8246271);
  if exists(select 1 from volunteers where id=p_id and active and role='admin') and
  (select count(*) from volunteers where active and role='admin' and archived_at is null)<=1 then raise exception 'Mantenha ao menos um administrador ativo.'; end if;
  if p_permanent then raise exception 'Desative o voluntário para preservar a autoria dos registros.'; end if;
  update volunteers set active=false,archived_at=now() where id=p_id;
 else raise exception 'Tipo de cadastro inválido.';
 end if;
 perform private.audit(p_kind,p_id,case when p_permanent then 'delete' else 'archive' end);
end $$;

-- Authenticated clients can read data, but cannot bypass the mutation rules.
do $$ declare table_name text; begin
 foreach table_name in array array['bases','volunteers','animals','base_transfers','adopters','adoptions','visits','visit_animals','vaccinations','treatments','treatment_doses','audit_events'] loop
  execute format('alter table public.%I enable row level security',table_name);
  execute format('create policy active_volunteers_read on public.%I for select to authenticated using (private.can_read())',table_name);
  execute format('revoke all on public.%I from anon,authenticated',table_name);
  execute format('grant select on public.%I to authenticated',table_name);
  execute format('grant all on public.%I to service_role',table_name);
 end loop;
end $$;
alter table invitation_allowlist enable row level security;
revoke all on invitation_allowlist from anon,authenticated;
grant all on invitation_allowlist to service_role;

create view public.needs_feed with (security_invoker=true) as
 with latest_vaccine as (
 select distinct on(animal_id,lower(name)) * from vaccinations where archived_at is null and administered_on is not null order by animal_id,lower(name),administered_on desc,created_at desc
 ), next_dose as (
 select distinct on(treatment_id) * from treatment_doses where given_at is null order by treatment_id,scheduled_at
 )
 select 'vaccine:'||v.id as id,'vaccine'::text as kind,v.name||' · '||a.name as title,'Próxima dose prevista'::text as subtitle,
 (v.next_due_on+time '09:00') at time zone 'America/Fortaleza' as due_at,a.id as animal_id,v.id as reference_id
 from latest_vaccine v join animals a on a.id=v.animal_id where v.next_due_on is not null and a.archived_at is null and a.status<>'deceased'
 and not exists(select 1 from vaccinations planned where planned.animal_id=v.animal_id and lower(planned.name)=lower(v.name) and planned.administered_on is null and planned.archived_at is null)
 union all
 select 'vaccine:'||v.id,'vaccine',v.name||' · '||a.name,'Vacina agendada',(v.scheduled_on+time '09:00') at time zone 'America/Fortaleza',a.id,v.id
 from vaccinations v join animals a on a.id=v.animal_id where v.administered_on is null and v.scheduled_on is not null and v.archived_at is null and a.archived_at is null and a.status<>'deceased'
 union all
 select 'dose:'||d.id,'dose',t.name||' · '||a.name,t.instructions,d.scheduled_at,a.id,d.id
 from next_dose d join treatments t on t.id=d.treatment_id join animals a on a.id=t.animal_id
 where t.status='active' and t.archived_at is null and a.archived_at is null and a.status<>'deceased'
 union all
 select 'visit:'||v.id,'visit','Visita · '||p.full_name,string_agg(a.name,', ' order by a.name),v.scheduled_at,null::uuid,v.id
 from visits v join adopters p on p.id=v.adopter_id join visit_animals va on va.visit_id=v.id join adoptions ad on ad.id=va.adoption_id join animals a on a.id=ad.animal_id
 where v.visited_at is null and v.cancelled_at is null and ad.ended_at is null and ad.followup_status='ongoing' group by v.id,p.full_name
 union all
 select 'followup:'||ad.id,case when c.completed=3 then 'closure' else 'unscheduled' end,
 case when c.completed=3 then 'Encerrar acompanhamento · ' else 'Agendar visita · ' end||a.name,
 p.full_name||' · '||c.completed||'/3 visitas',null::timestamptz,a.id,ad.id
 from adoptions ad join animals a on a.id=ad.animal_id join adopters p on p.id=ad.adopter_id
 cross join lateral (select count(*) filter(where v.visited_at is not null and va.outcome is not null) as completed,
 count(*) filter(where v.visited_at is null) as pending from visit_animals va join visits v on v.id=va.visit_id where va.adoption_id=ad.id and v.cancelled_at is null) c
 where ad.ended_at is null and ad.followup_status='ongoing' and c.pending=0;
grant select on needs_feed to authenticated;

create function public.get_snapshot() returns jsonb language plpgsql security invoker set search_path=public,pg_temp as $$
begin
 if not private.can_read() then raise exception 'Seu acesso está pendente ou foi desativado.' using errcode='42501'; end if;
 return jsonb_build_object(
 'current_user_id',auth.uid(),
 'bases',(select coalesce(jsonb_agg(t),'[]') from bases t),
 'volunteers',(select coalesce(jsonb_agg(t order by full_name),'[]') from volunteers t),
 'animals',(select coalesce(jsonb_agg(t order by name),'[]') from animals t),
 'transfers',(select coalesce(jsonb_agg(t order by happened_at desc),'[]') from base_transfers t),
 'adopters',(select coalesce(jsonb_agg(t order by full_name),'[]') from adopters t),
 'adoptions',(select coalesce(jsonb_agg(t order by created_at desc),'[]') from adoptions t),
 'visits',(select coalesce(jsonb_agg(t order by scheduled_at desc),'[]') from visits t),
 'visit_animals',(select coalesce(jsonb_agg(t),'[]') from visit_animals t),
 'vaccinations',(select coalesce(jsonb_agg(t order by administered_on desc),'[]') from vaccinations t),
 'treatments',(select coalesce(jsonb_agg(t order by created_at desc),'[]') from treatments t),
 'doses',(select coalesce(jsonb_agg(t order by scheduled_at),'[]') from treatment_doses t),
 'feed',(select coalesce(jsonb_agg(t order by due_at nulls last,title),'[]') from needs_feed t));
end $$;

revoke all on all functions in schema private from public,anon,authenticated;
grant execute on function private.can_read() to authenticated;
revoke all on function public.save_record(text,jsonb),public.create_adoption(jsonb),public.schedule_visit(jsonb),public.record_visit(jsonb),public.cancel_visit(uuid),public.close_followup(uuid,text,text),public.record_dose(uuid,timestamptz,text),public.suspend_treatment(uuid),public.set_volunteer_role(uuid,text),public.archive_record(text,uuid,boolean),public.get_snapshot() from public,anon;
grant execute on function public.save_record(text,jsonb),public.create_adoption(jsonb),public.schedule_visit(jsonb),public.record_visit(jsonb),public.cancel_visit(uuid),public.close_followup(uuid,text,text),public.record_dose(uuid,timestamptz,text),public.suspend_treatment(uuid),public.set_volunteer_role(uuid,text),public.archive_record(text,uuid,boolean),public.get_snapshot() to authenticated;
