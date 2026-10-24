package com.foodscan.common;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the user id from an access token; the JWT subject is the user id. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
