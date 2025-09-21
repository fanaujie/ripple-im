package com.fanaujie.ripple.msggateway.server.jwt;

import com.fanaujie.ripple.msggateway.exception.WsJwtDecodedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public interface JwtDecoder {
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    class DecodedJwtClaims {
        private String subject;
    }

    DecodedJwtClaims decodeJwtClaims(String token) throws WsJwtDecodedException;
}
