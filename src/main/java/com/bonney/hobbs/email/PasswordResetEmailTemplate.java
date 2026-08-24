package com.bonney.hobbs.email;

public class PasswordResetEmailTemplate implements EmailTemplate {

    private final String name;
    private final String link;
    private final String code;
    private final int ttlMinutes;

    public PasswordResetEmailTemplate(String name, String link, String code, int ttlMinutes) {
        this.name = name;
        this.link = link;
        this.code = code;
        this.ttlMinutes = ttlMinutes;
    }

    @Override
    public String subject() {
        return "Reset your Hobbs... password";
    }

    @Override
    public String htmlBody() {
        String message = "We received a request to reset your <strong>Hobbs&hellip;</strong> password. This code "
                + "expires in " + ttlMinutes + " minutes &mdash; if you didn't request this, you can safely ignore this email.";
        String card = EmailLayout.actionCard("Reset password", link, "Or enter this code on the reset page:", code);
        return EmailLayout.wrap(EmailLayout.greeting(name), message, card);
    }
}
