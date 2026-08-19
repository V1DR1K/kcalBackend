create table day_preset (
    id BIGSERIAL,
    user_id bigint not null,
    name varchar(120) not null,
    items_json TEXT not null,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    primary key (id),
    constraint fk_day_preset_user foreign key (user_id) references users(id)
);

create unique index ux_day_preset_user_name_active
    on day_preset(user_id, name)
    where deleted_at is null;
create index ix_day_preset_user_active
    on day_preset(user_id, deleted_at, updated_at);
