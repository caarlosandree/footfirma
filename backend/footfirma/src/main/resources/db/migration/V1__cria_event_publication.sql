-- Registro de publicação de eventos do Spring Modulith.
-- O spring-modulith-starter-jpa mapeia a entidade DefaultJpaEventPublication para esta
-- tabela; com ddl-auto=validate ela precisa existir antes de qualquer entidade nossa,
-- senão o contexto nem sobe. O DDL espelha org.springframework.modulith.events.jpa.
create table event_publication (
    id                     uuid        not null primary key,
    listener_id            text        not null,
    event_type             text        not null,
    serialized_event       text        not null,
    publication_date       timestamptz not null,
    completion_date        timestamptz,
    last_resubmission_date timestamptz,
    completion_attempts    integer     not null default 0,
    status                 text
);

create index idx_event_publication_completion_date on event_publication (completion_date);
create index idx_event_publication_listener_serialized on event_publication (listener_id, serialized_event);
