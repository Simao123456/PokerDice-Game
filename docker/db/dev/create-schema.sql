BEGIN;

create table if not exists Invitation (
    code	varchar(64) primary key,
    is_active	boolean not null default true
    --user_id	int not null references User(user_id) on delete cascade,
    );

create table if not exists Users (
        user_id int generated always as identity primary key,
        name	varchar(64) unique not null,
        password	varchar(256) not null,
        email	varchar (128) unique not null,
        balance		numeric(10,2) not null default 0
);

create table if not exists Sessions (
        session_id	varchar(255) primary key,
        user_id	int not null references Users(user_id) on delete cascade,
        created_at	bigint not null,
        last_used_at	bigint not null,
        expires_at	bigint not null,
        revoked	boolean not null default false
);

create table if not exists Lobby (
        lobby_id	serial primary key,
        name	varchar(64) not null,
        description	text,
        status	varchar(32) not null default 'WAITING',
        host_id	int not null references Users(user_id),
        min_players	int not null,
        max_players	int not null,
        max_rounds	int not null,
        timeout_seconds	int not null,
        created_at	bigint not null
);

create table if not exists Lobby_Users (
        lobby_id	int not null references Lobby(lobby_id) on delete cascade,
        user_id	int not null references Users(user_id) on delete cascade,
        joined_at	bigint not null,
        primary key (lobby_id, user_id)
);

create table if not exists Match (
        match_id	serial primary key,
        lobby_id	int not null references Lobby(lobby_id),
        status	varchar(32) not null default 'ongoing',
        starting_player_user_id int not null references Users(user_id),
        current_round_id	int,
        created_at	bigint not null,
        finished_at	bigint
);
create table if not exists Round (
        round_id	serial primary key,
        match_id	int not null references Match(match_id) on delete cascade,
        number	int not null,
        blind	numeric(10,2) not null default 1,
        pot	numeric(10,2) not null default 0,
        winner_user_id	int references Users(user_id),
        unique (match_id, number)
);

create table if not exists Hand (
        hand_id		serial primary key,
        round_id	int not null references Round(round_id) on delete cascade,
        user_id		int not null references Users(user_id),
        faces 		varchar(64) not null,
        rank 		int not null,
        tie_breaker_key int,
        unique 		(round_id, user_id)
);

create table if not exists Turn (
        turn_id		serial primary key,
        round_id	int not null references Round(round_id) on delete cascade,
        user_id		int not null references Users(user_id),
        state		varchar(32) not null default 'active',
        number             int not null,
        roll_count         int not null default 0,
        unique (round_id, user_id)
);

create table if not exists Roll (
        roll_id            serial primary key,
        turn_id            int not null references Turn(turn_id) on delete cascade,
        created_at         bigint not null,
        held_mask          varchar(5) not null,
        dice_values        varchar(32) not null
);


COMMIT;


