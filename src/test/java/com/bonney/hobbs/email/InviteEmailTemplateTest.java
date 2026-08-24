package com.bonney.hobbs.email;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

class InviteEmailTemplateTest {

    @Test
    void statesExpiryInWholeDaysWhenEvenlyDivisibleBy24() {
        InviteEmailTemplate template = new InviteEmailTemplate("Alice", "http://localhost/create-pilot", "abc123", 168);

        assertThat(template.htmlBody(), containsString("This invite expires in 7 days"));
    }

    @Test
    void statesExpiryInHoursWhenNotEvenlyDivisibleByADay() {
        InviteEmailTemplate template = new InviteEmailTemplate("Alice", "http://localhost/create-pilot", "abc123", 30);

        assertThat(template.htmlBody(), containsString("This invite expires in 30 hours"));
    }

    @Test
    void singularHourAndDayAreGrammaticallyCorrect() {
        InviteEmailTemplate oneHour = new InviteEmailTemplate("Alice", "http://localhost/create-pilot", "abc123", 1);
        InviteEmailTemplate oneDay = new InviteEmailTemplate("Alice", "http://localhost/create-pilot", "abc123", 24);

        assertThat(oneHour.htmlBody(), containsString("This invite expires in 1 hour &mdash;"));
        assertThat(oneDay.htmlBody(), containsString("This invite expires in 1 day &mdash;"));
    }

    @Test
    void includesTheInviteCodeAndLink() {
        InviteEmailTemplate template = new InviteEmailTemplate("Alice", "http://localhost/create-pilot?code=abc123", "abc123", 168);

        assertThat(template.htmlBody(), containsString("abc123"));
        assertThat(template.htmlBody(), containsString("http://localhost/create-pilot?code=abc123"));
    }
}
