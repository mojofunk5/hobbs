package com.bonney.hobbs.domain;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Opted out of the suite-wide concurrent default (see src/test/resources/junit-platform.properties)
// - this class's @Test methods share one GreenMail SMTP server via static fields set in @BeforeAll,
// unlike every other test class's per-method @BeforeEach fixtures, so its methods aren't independent.
@Execution(ExecutionMode.SAME_THREAD)
class SmtpEmailSenderTest {

    private static GreenMail greenMail;

    @BeforeAll
    static void startSmtpServer() {
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.withConfiguration(GreenMailConfiguration.aConfig().withUser("testuser", "testpass"));
        greenMail.start();
    }

    @AfterAll
    static void stopSmtpServer() {
        greenMail.stop();
    }

    @Test
    void sendsARealMessageOverSmtp() throws Exception {
        SmtpEmailSender sender = new SmtpEmailSender("localhost", greenMail.getSmtp().getPort(),
                "testuser", "testpass", "noreply@hobbs.test");

        sender.send("recipient@example.com", "Test subject", "<p>Test body</p>");

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received.length, is(1));
        assertThat(received[0].getSubject(), is("Test subject"));
        assertThat(received[0].getAllRecipients()[0].toString(), is("recipient@example.com"));
        assertThat((String) received[0].getContent(), containsString("Test body"));
    }

    @Test
    void wrapsAConnectionFailureAsEmailSendException() {
        SmtpEmailSender sender = new SmtpEmailSender("localhost", 1, "testuser", "testpass", "noreply@hobbs.test");

        assertThrows(EmailSendException.class,
                () -> sender.send("recipient@example.com", "Test subject", "<p>Test body</p>"));
    }
}
