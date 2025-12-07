-- Create ripple keyspace
CREATE KEYSPACE IF NOT EXISTS ripple
    WITH replication = {
        'class': 'SimpleStrategy',
        'replication_factor': 1
        };

CREATE TABLE IF NOT EXISTS "ripple"."user"
(
    "account"  text,
    "user_id"  bigint,
    "password" text,
    "role"     text,
    "status"   tinyint,
    primary key ( account )
);

CREATE TABLE IF NOT EXISTS "ripple"."user_profile"
(
    "user_id"   bigint,
    "account"   text,
    "nick_name" text,
    "avatar"    text,
    primary key ( user_id )
);

CREATE TABLE IF NOT EXISTS "ripple"."user_relations"
(
    "user_id"          bigint,
    "relation_user_id" bigint,
    "nick_name"        text,
    "avatar"           text,
    "remark_name"      text,
    "relation_flags"   tinyint, -- bit0: is_friend, bit1: is_blocked, bit2: is_hidden
    primary key ( user_id, relation_user_id)
);


CREATE TABLE IF NOT EXISTS "ripple"."user_relation_version"
(
    "user_id"          bigint,
    "version"          timeuuid,
    "relation_user_id" bigint,
    "operation"        tinyint, -- 1: add 2:update, 3:delete
    "nick_name"        text,
    "avatar"           text,
    "remark_name"      text,
    "relation_flags"   tinyint, -- bit0: is_friend, bit1: is_blocked, bit2: is_hidden
    primary key ( user_id, version )
);


CREATE TABLE IF NOT EXISTS "ripple"."user_blocked_by"
(
    "blocked_user_id" bigint,
    "blocker_user_id" bigint,
    primary key ( blocked_user_id, blocker_user_id )
);

CREATE TABLE IF NOT EXISTS "ripple"."user_conversations"
(
    "owner_id"             bigint,
    "conversation_id"      text,
    "peer_id"              bigint,
    "group_id"             bigint,
    "last_read_message_id" bigint,
    "name"                 text,
    "avatar"               text,
    primary key (owner_id, conversation_id)
);

CREATE TABLE IF NOT EXISTS "ripple"."user_conversations_version"
(
    "user_id"              bigint,
    "version"              timeuuid,
    "conversation_id"      text,
    "peer_id"              bigint,
    "group_id"             bigint,
    "operation"            tinyint,
    "last_read_message_id" bigint,
    "name"                 text,
    "avatar"               text,
    primary key ( user_id, version )
);

CREATE TABLE IF NOT EXISTS "ripple"."user_messages"
(
    "conversation_id" text,
    "message_id"      bigint,
    "sender_id"       bigint,
    "receiver_id"     bigint,
    "group_id"        bigint,
    "send_timestamp"  bigint,
    "message_type"    tinyint, -- 1: TEXT, 2: FILE, 3: GROUP_COMMAND
    "text"            text,
    "file_url"        text,
    "file_name"       text,
    "command_type"    tinyint, -- 1: MEMBER_JOIN, 2: MEMBER_QUIT
    "command_data"    text,
    primary key ( conversation_id, message_id )
);


CREATE TABLE IF NOT EXISTS "ripple"."groups"
(
    "group_id"     bigint,
    "group_name"   text,
    "group_avatar" text,
    "member_ids"   set<bigint>,
    "version"      bigint,
    primary key (group_id)
);

CREATE TABLE IF NOT EXISTS "ripple"."group_members_info"
(
    "group_id" bigint,
    "user_id"  bigint,
    "name"     text,
    "avatar"   text,
    primary key (group_id, user_id)
);


CREATE TABLE IF NOT EXISTS "ripple"."user_groups"
(
    "user_id"  bigint,
    "group_id" bigint,
    primary key ( user_id, group_id)
);


