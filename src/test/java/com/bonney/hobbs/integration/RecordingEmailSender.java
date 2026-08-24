package com.bonney.hobbs.integration;

import com.bonney.hobbs.domain.EmailSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double that records every send() call instead of hitting real SMTP - lets tests assert what
 * would have been sent without needing a mail server for the whole integration suite (most tests
 * register a pilot, which now goes through the invite flow).
 */
public class RecordingEmailSender implements EmailSender {

    public record SentEmail(String toAddress, String subject, String htmlBody) {
    }

    private final List<SentEmail> sent = new ArrayList<>();

    @Override
    public void send(String toAddress, String subject, String htmlBody) {
        sent.add(new SentEmail(toAddress, subject, htmlBody));
    }

    public List<SentEmail> getSent() {
        return sent;
    }
}
