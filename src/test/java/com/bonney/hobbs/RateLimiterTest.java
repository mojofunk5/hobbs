package com.bonney.hobbs;

import com.bonney.hobbs.domain.RateLimitRepository;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

// RateLimiter's own remaining job is just working out the right client IP (trusting
// X-Forwarded-For only when a proxy is known to front the app) and translating the repository's
// allow/reject decision into a response - the actual counting/limiting logic now lives in
// RateLimitRepository (see RateLimitRepositoryTest).
@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    Context ctx;

    @Mock
    RateLimitRepository repository;

    RateLimiter rateLimiter;

    @Test
    void allowedRequestPassesThrough() {
        rateLimiter = new RateLimiter(repository, 10, false);
        when(ctx.ip()).thenReturn("127.0.0.1");
        when(repository.tryConsume("127.0.0.1", 10)).thenReturn(true);

        rateLimiter.handle(ctx);

        verify(ctx, never()).status(HttpStatus.TOO_MANY_REQUESTS);
        verify(ctx, never()).skipRemainingHandlers();
    }

    @Test
    void rejectedRequestReturnsTooManyRequests() {
        rateLimiter = new RateLimiter(repository, 10, false);
        when(ctx.ip()).thenReturn("127.0.0.1");
        when(repository.tryConsume("127.0.0.1", 10)).thenReturn(false);
        when(ctx.status(HttpStatus.TOO_MANY_REQUESTS)).thenReturn(ctx);

        rateLimiter.handle(ctx);

        verify(ctx).status(HttpStatus.TOO_MANY_REQUESTS);
        verify(ctx).skipRemainingHandlers();
    }

    @Test
    void ignoresForwardedForHeaderWhenProxyNotTrusted() {
        rateLimiter = new RateLimiter(repository, 10, false);
        when(ctx.ip()).thenReturn("127.0.0.1");
        when(repository.tryConsume("127.0.0.1", 10)).thenReturn(true);

        rateLimiter.handle(ctx);

        verify(repository).tryConsume("127.0.0.1", 10);
        verify(ctx, never()).header("X-Forwarded-For");
    }

    @Test
    void usesForwardedForHeaderWhenProxyTrusted() {
        rateLimiter = new RateLimiter(repository, 10, true);
        when(ctx.header("X-Forwarded-For")).thenReturn("203.0.113.1");
        when(repository.tryConsume("203.0.113.1", 10)).thenReturn(true);

        rateLimiter.handle(ctx);

        verify(repository).tryConsume("203.0.113.1", 10);
        verify(ctx, never()).ip();
    }

    @Test
    void usesFirstEntryOfForwardedForChain() {
        rateLimiter = new RateLimiter(repository, 10, true);
        when(ctx.header("X-Forwarded-For")).thenReturn("203.0.113.5, 172.18.0.1");
        when(repository.tryConsume("203.0.113.5", 10)).thenReturn(true);

        rateLimiter.handle(ctx);

        verify(repository).tryConsume("203.0.113.5", 10);
    }

    @Test
    void fallsBackToDirectIpWhenProxyTrustedButHeaderMissing() {
        rateLimiter = new RateLimiter(repository, 10, true);
        when(ctx.header("X-Forwarded-For")).thenReturn(null);
        when(ctx.ip()).thenReturn("127.0.0.1");
        when(repository.tryConsume("127.0.0.1", 10)).thenReturn(true);

        rateLimiter.handle(ctx);

        verify(repository).tryConsume("127.0.0.1", 10);
    }
}
