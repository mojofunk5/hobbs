package com.bonney.hobbs.email;

// Inline styles only, no CSS custom properties - most email clients (including Gmail) strip <style>
// blocks and don't support var(...), so the literal hex/font values from hobbs-ui's own
// design-system/styles/global.css are duplicated here rather than referenced.
public final class EmailLayout {

    private EmailLayout() {
        super();
    }

    public static String greeting(String name) {
        return (name != null && !name.isBlank()) ? "Hi " + name : "Hi";
    }

    public static String actionCard(String buttonText, String link, String codeIntro, String code) {
        return """
                <div style="border: 1px solid #e5e5e5; border-radius: 8px; padding: 24px; text-align: center;">
                  <a href="%s" style="display: inline-block; background-color: #0a0a0a; color: #ffffff; font-size: 14px; font-weight: 500; text-decoration: none; padding: 10px 24px; border-radius: 4px;">%s</a>
                  <p style="font-size: 13px; color: #6b6b6b; margin: 16px 0 6px;">%s</p>
                  <p style="font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 14px; background-color: #f5f5f5; padding: 8px 12px; border-radius: 4px; display: inline-block; margin: 0;">%s</p>
                </div>""".formatted(link, buttonText, codeIntro, code);
    }

    public static String wrap(String greeting, String messageHtml, String cardHtml) {
        return """
                <div style="font-family: -apple-system, BlinkMacSystemFont, 'Inter', ui-sans-serif, system-ui, sans-serif; max-width: 480px; margin: 0 auto; padding: 40px 24px; color: #0a0a0a;">
                  <p style="font-size: 24px; font-weight: 600; letter-spacing: -0.025em; margin: 0 0 32px;">Hobbs&hellip;</p>
                  <p style="font-size: 16px; margin: 0 0 12px;">%s,</p>
                  <p style="font-size: 16px; line-height: 1.5; margin: 0 0 28px; color: #333333;">%s</p>
                  %s
                  <p style="font-size: 14px; line-height: 1.6; color: #333333; margin: 28px 0 0;">Love,<br>Andy and the team @bssd.co.uk</p>
                </div>
                """.formatted(greeting, messageHtml, cardHtml);
    }
}
