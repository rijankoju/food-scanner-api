package com.foodscan.auth;

import com.foodscan.user.Profile;
import com.foodscan.user.ProfileRepository;
import com.foodscan.user.User;
import com.foodscan.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public record Tokens(String accessToken, String refreshToken, long expiresIn, String tokenType) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final ProfileRepository profiles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    // Compared against when the email is unknown, so login takes the same
    // time whether or not an account exists.
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, ProfileRepository profiles, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, AuthProperties properties,
                       Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public Tokens register(String email, String password, String displayName) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = users.save(new User(normalized, passwordEncoder.encode(password)));
        Profile profile = new Profile(user.getId());
        profile.setDisplayName(displayName);
        profiles.save(profile);
        return issueTokens(user.getId());
    }

    @Transactional
    public Tokens login(String email, String password) {
        User user = users.findByEmail(normalize(email)).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        if (!passwordEncoder.matches(password, hash) || user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return issueTokens(user.getId());
    }

    /**
     * Exchanges a refresh token for new tokens and revokes the old one. If an
     * already-revoked token is presented it may have been stolen, so every
     * session of that user is revoked (the revocation must commit even though
     * the request fails, hence noRollbackFor).
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Tokens refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (token.getRevokedAt() != null) {
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            throw invalidRefreshToken();
        }
        if (!token.isActive(now)) {
            throw invalidRefreshToken();
        }
        token.revoke(now);
        return issueTokens(token.getUserId());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(hash(refreshToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    private Tokens issueTokens(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("foodscan")
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .build();
        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokens.save(new RefreshToken(userId, hash(refreshToken), now.plus(properties.refreshTokenTtl())));

        return new Tokens(accessToken, refreshToken, properties.accessTokenTtl().toSeconds(), "Bearer");
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalidRefreshToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
