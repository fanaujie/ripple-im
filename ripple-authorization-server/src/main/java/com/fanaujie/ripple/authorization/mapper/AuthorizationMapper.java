package com.fanaujie.ripple.authorization.mapper;

import com.fanaujie.ripple.authorization.model.mapper.Authorization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Mapper
public interface AuthorizationMapper {

    void save(Authorization authorization);

    void deleteById(String id);

    Optional<Authorization> findById(String id);

    Optional<Authorization> findByState(String state);

    Optional<Authorization> findByAuthorizationCodeValue(String authorizationCodeValue);

    Optional<Authorization> findByAccessTokenValue(String accessTokenValue);

    Optional<Authorization> findByRefreshTokenValue(String refreshTokenValue);

    Optional<Authorization> findByOidcIdTokenValue(String oidcIdTokenValue);

    Optional<Authorization> findByUserCodeValue(String userCodeValue);

    Optional<Authorization> findByDeviceCodeValue(String deviceCodeValue);

    Optional<Authorization> findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValueOrOidcIdTokenValueOrUserCodeValueOrDeviceCodeValue(String token);
}