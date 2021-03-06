package org.jenkinsci.test.acceptance.utils.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.test.acceptance.ByFactory;
import org.jenkinsci.test.acceptance.plugins.mailer.MailerGlobalConfig;
import org.jenkinsci.test.acceptance.po.PageObject;
import org.openqa.selenium.WebElement;

import javax.inject.Singleton;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link MailService} that uses Mailtrap.io
 *
 * This class comes with the default account shared by the project, but
 * you can also specify a separate account from wiring script like this:
 *
 * <pre>
 * bind MailService toInstance new Mailtrap(...);
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 */
@Singleton
public class Mailtrap extends MailService {
    // these default values is the account that the project "owns".

    private String MAILBOX = "19251ad93afaab19b";
    private String PASSWORD = "c9039d1f090624";
    private String TOKEN = "2c04434bd66dfc37c130171f9d061af2";
    private String INBOX_ID = "23170";

    /**
     * Unique ID for this test run.
     *
     * This is also the email address that emails should be sent to, if you want
     * to test emails.
     */
    public final String recipient;

    public Mailtrap() {
        recipient = PageObject.createRandomName() + "@" + MAILBOX + ".com";
    }

    /**
     * This constructor allow you to override values from wiring script.
     */
    public Mailtrap(String MAILBOX, String PASSWORD, String TOKEN, String INBOX_ID) {
        this();
        this.MAILBOX = MAILBOX;
        this.PASSWORD = PASSWORD;
        this.TOKEN = TOKEN;
        this.INBOX_ID = INBOX_ID;
    }

    /**
     * Set up the configuration to use the shared mailtrap.io account.
     */
    @Override
    public void setup(MailerGlobalConfig config) {
        config.smtpServer.set("mailtrap.io");
        config.advancedButton.click();
        config.useSMTPAuth.check();
        config.smtpAuthUserName.set(MAILBOX);
        config.smtpAuthPassword.set(PASSWORD);
        config.smtpPort.set("2525");

        // Fingerprint to identify message sent from this test run
        config.replyToAddress.set(recipient);

        // Set for email-ext plugin as well if available
        WebElement e = config.getElement(by.path("/hudson-plugins-emailext-ExtendedEmailPublisher/ext_mailer_default_replyto"));
        if (e!=null)
            e.sendKeys(recipient);
    }

    /**
     * @return null if nothing found.
     */
    @Override
    public MimeMessage getMail(Pattern subject) throws IOException {
        List<MimeMessage> match = new ArrayList<>();

        for (JsonNode msg : fetchMessages()) {
            if (subject.matcher(msg.get("subject").asText()).find()) {
                MimeMessage m = fetchMessage(msg.get("id").asText());
                if (isOurs(m)) {
                    match.add(m);
                }
            }
        }

        switch (match.size()) {
            case 0: return null;
            case 1: return match.get(0);
            default: throw new AssertionError("More than one matching message found");
        }
    }

    @Override
    public List<MimeMessage> getAllMails() throws IOException {
        List<MimeMessage> match = new ArrayList<>();

        for (JsonNode msg : fetchMessages()) {
            MimeMessage m = fetchMessage(msg.get("id").asText());
            if (isOurs(m))
                match.add(m);
        }

        return match;
    }

    /**
     * Does this email belong to our test case (as opposed to other tests that might be running elsewhere?)
     */
    private boolean isOurs(MimeMessage m) {
        try {
            Address[] r = m.getReplyTo();
            if (r==null)    return false;
            for (Address a : r) {
                if (a.toString().contains(recipient))
                    return true;
            }
            return false;
        } catch (MessagingException e) {
            throw new AssertionError(e);
        }
    }

    public JsonNode fetchJson(String fmt, Object... args) throws IOException {
        String s = IOUtils.toString(new URL(String.format(fmt, args)).openStream());
        return new ObjectMapper().readTree(s);
    }

    public JsonNode fetchMessages() throws IOException {
        return fetchJson("https://mailtrap.io/api/v1/inboxes/%s/messages?page=1&api_token=%s", INBOX_ID, TOKEN);
    }

    public MimeMessage fetchMessage(String id) throws IOException {
        URL raw = new URL(String.format("https://mailtrap.io/api/v1/inboxes/%s/messages/%s/body.eml?api_token=%s", INBOX_ID, id, TOKEN));

        try {
            return new MimeMessage(Session.getDefaultInstance(System.getProperties()), raw.openStream());
        } catch (MessagingException e) {
            throw new IOException(e);
        }
    }

    private static final ByFactory by = new ByFactory();
}
