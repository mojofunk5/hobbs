package com.bonney.hobbs;

import com.bonney.hobbs.domain.RateLimitRepository;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

public class RateLimiter {

    private final RateLimitRepository repository;
    private final int requestsPerSecond;
    private final boolean trustProxy;

    public RateLimiter(RateLimitRepository repository, int requestsPerSecond, boolean trustProxy) {
        this.repository = repository;
        this.requestsPerSecond = requestsPerSecond;
        this.trustProxy = trustProxy;
    }

    public void handle(Context ctx) {
        if (!repository.tryConsume(clientIp(ctx), requestsPerSecond)) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS);
            ctx.skipRemainingHandlers();
        }
    }

    /**
     * Only trusts X-Forwarded-For when trustProxy is enabled (i.e. Caddy is known to be the only
     * thing that can reach this app - it's not published directly, see docker-compose.yml). Blindly
     * trusting this header from any caller would let an attacker spoof it to bypass the rate limit.
     */
    private String clientIp(Context ctx) {
        if (trustProxy) {
            String forwardedFor = ctx.header("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return ctx.ip();
    }
}
