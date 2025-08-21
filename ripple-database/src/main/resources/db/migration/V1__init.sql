
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    role VARCHAR(20) DEFAULT 'USER',
    created_time datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_time datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE user_profile (
    user_id BIGINT PRIMARY KEY NOT NULL,
    user_type tinyint DEFAULT '0',
    nick_name varchar(50) DEFAULT NULL,
    avatar varchar(200) DEFAULT NULL,
    created_time datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_time datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE authorization (
   id varchar(255) NOT NULL,
   registeredClientId varchar(255) NOT NULL,
   principalName varchar(255) NOT NULL,
   authorizationGrantType varchar(255) NOT NULL,
   authorizedScopes TEXT DEFAULT NULL,
   attributes TEXT DEFAULT NULL,
   state varchar(500) DEFAULT NULL,
   authorizationCodeValue TEXT DEFAULT NULL,
   authorizationCodeIssuedAt timestamp DEFAULT NULL,
   authorizationCodeExpiresAt timestamp DEFAULT NULL,
   authorizationCodeMetadata TEXT DEFAULT NULL,
   accessTokenValue TEXT DEFAULT NULL,
   accessTokenIssuedAt timestamp DEFAULT NULL,
   accessTokenExpiresAt timestamp DEFAULT NULL,
   accessTokenMetadata TEXT DEFAULT NULL,
   accessTokenType varchar(255) DEFAULT NULL,
   accessTokenScopes TEXT DEFAULT NULL,
   refreshTokenValue TEXT DEFAULT NULL,
   refreshTokenIssuedAt timestamp DEFAULT NULL,
   refreshTokenExpiresAt timestamp DEFAULT NULL,
   refreshTokenMetadata TEXT DEFAULT NULL,
   oidcIdTokenValue TEXT DEFAULT NULL,
   oidcIdTokenIssuedAt timestamp DEFAULT NULL,
   oidcIdTokenExpiresAt timestamp DEFAULT NULL,
   oidcIdTokenMetadata TEXT DEFAULT NULL,
   oidcIdTokenClaims TEXT DEFAULT NULL,
   userCodeValue TEXT DEFAULT NULL,
   userCodeIssuedAt timestamp DEFAULT NULL,
   userCodeExpiresAt timestamp DEFAULT NULL,
   userCodeMetadata TEXT DEFAULT NULL,
   deviceCodeValue TEXT DEFAULT NULL,
   deviceCodeIssuedAt timestamp DEFAULT NULL,
   deviceCodeExpiresAt timestamp DEFAULT NULL,
   deviceCodeMetadata TEXT DEFAULT NULL,
   PRIMARY KEY (id)
);