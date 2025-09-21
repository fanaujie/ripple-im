package com.fanaujie.ripple.msggateway.server.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fanaujie.ripple.msggateway.exception.WsJwtDecodedException;

import java.util.Base64;

public class DefaultJwtDecoder implements JwtDecoder {
    private final JWTVerifier verifier;

    public DefaultJwtDecoder(String jwkSecret) {
        Algorithm algorithm = Algorithm.HMAC256(Base64.getDecoder().decode(jwkSecret));
        this.verifier = JWT.require(algorithm).build();
    }

    public DecodedJWT validate(String token) throws JWTVerificationException {
        return verifier.verify(token);
    }

    @Override
    public DecodedJwtClaims decodeJwtClaims(String token) throws WsJwtDecodedException {
        try {
            return new DecodedJwtClaims(verifier.verify(token).getSubject());
        } catch (JWTVerificationException e) {
            throw new WsJwtDecodedException("Failed to decode JWT: " + e.getMessage());
        }
    }
}
