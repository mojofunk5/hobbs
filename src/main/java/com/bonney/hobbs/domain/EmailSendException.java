package com.bonney.hobbs.domain;

/**
 * Wraps a checked {@code jakarta.mail.MessagingException} as unchecked. Unlike the domain exceptions
 * that map to an HTTP status (see {@code HobbsApplication}'s exception handlers), this one is meant
 * to be caught and logged by the caller, not propagated to the client - a failed send shouldn't fail
 * whatever triggered it.
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(Throwable cause) {
        super("Failed to send email", cause);
    }
}
