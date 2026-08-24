package com.bonney.hobbs.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class AdminBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AtomicReference<String> bootstrapCode = new AtomicReference<>();

    public AdminBootstrap(AdminRepository adminRepository) {
        if (adminRepository.isEmpty()) {
            String code = UUID.randomUUID().toString();
            bootstrapCode.set(code);
            logger.warn("No admin account exists. Use this one-time referral code to register the first admin: {}", code);
        }
    }

    public String getBootstrapCode() {
        return bootstrapCode.get();
    }

    /**
     * Atomically checks whether {@code code} is the current bootstrap code and, if so, consumes it.
     * Only one of any number of concurrent callers presenting the correct code will receive {@code true}.
     *
     * <p>Compares by value (not {@link AtomicReference#compareAndSet}'s reference identity) since
     * {@code code} arrives as a freshly-deserialized string over HTTP, not the same instance held here.
     * A single compare-and-set is enough, no retry loop: this field only ever makes one transition
     * (the generated code to {@code null}), so if the CAS fails, another caller already won the race
     * and the answer is unambiguously {@code false}.
     */
    boolean tryConsumeBootstrapCode(String code) {
        if (code == null) {
            return false;
        }
        String current = bootstrapCode.get();
        return code.equals(current) && bootstrapCode.compareAndSet(current, null);
    }
}
