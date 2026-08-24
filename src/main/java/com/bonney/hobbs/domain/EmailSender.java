package com.bonney.hobbs.domain;

/**
 * Sends an email. Deliberately narrow (address, subject, body) rather than a broader "notification"
 * abstraction - not designed to cover hypothetical future channels (SMS, WhatsApp) up front, but this
 * is the seam where an alternative implementation would plug in if one's ever needed.
 */
public interface EmailSender {

    void send(String toAddress, String subject, String htmlBody);
}
