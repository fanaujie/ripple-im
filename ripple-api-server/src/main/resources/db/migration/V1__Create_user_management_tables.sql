
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE authorities (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL
);
CREATE TABLE user_roles (
    user_id BIGINT,
    role_id BIGINT,
    PRIMARY KEY (user_id, role_id)
);
CREATE TABLE role_authorities (
    role_id BIGINT,
    authority_id BIGINT,
    PRIMARY KEY (role_id, authority_id)
);

INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO authorities (name) VALUES
       ('SEND_MESSAGE'),
       ('READ_MESSAGE'),
       ('MANAGE_CONTACTS'),
       ('CREATE_GROUP'),
       ('JOIN_GROUP'),
       ('VIEW_PROFILE'),
       ('EDIT_PROFILE'),
       ('UPLOAD_FILE'),
       ('DOWNLOAD_FILE'),
       ('BLOCK_USER'),
       ('SEARCH_USER');
INSERT INTO role_authorities (role_id, authority_id) VALUES
       (1, 1),
       (1, 2),
       (1, 3),
       (1, 4),
       (1, 5),
       (1, 6),
       (1, 7),
       (1, 8),
       (1, 9),
       (1, 10),
       (1, 11);