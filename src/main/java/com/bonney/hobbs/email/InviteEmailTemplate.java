package com.bonney.hobbs.email;

public class InviteEmailTemplate implements EmailTemplate {

    private final String name;
    private final String link;
    private final String code;
    private final int ttlHours;

    public InviteEmailTemplate(String name, String link, String code, int ttlHours) {
        this.name = name;
        this.link = link;
        this.code = code;
        this.ttlHours = ttlHours;
    }

    @Override
    public String subject() {
        return "You're invited to Hobbs...";
    }

    @Override
    public String htmlBody() {
        String message = "You've been invited to <strong>Hobbs</strong> &mdash; a flight logbook for logging your "
                + "flights, with GPS recording to pre-fill entries when you want it. This invite expires in "
                + formatTtl(ttlHours) + " &mdash; if it lapses, just ask whoever invited you to send a new one.";
        String card = EmailLayout.actionCard("Create your account", link, "Or enter this code on the sign-up page:", code);
        return EmailLayout.wrap(EmailLayout.greeting(name), message, card);
    }

    // 168 reads far better as "7 days" than "168 hours" - falls back to hours for anything not an
    // exact whole number of days, so an unusual property value (e.g. 30) still renders sensibly.
    private static String formatTtl(int hours) {
        if (hours % 24 == 0) {
            int days = hours / 24;
            return days + (days == 1 ? " day" : " days");
        }
        return hours + (hours == 1 ? " hour" : " hours");
    }
}
