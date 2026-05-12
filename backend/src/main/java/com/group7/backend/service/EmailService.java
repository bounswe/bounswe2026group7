package com.group7.backend.service;

import com.group7.backend.entity.User;
import com.group7.backend.exception.EmailSendException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Production EmailService. Bean is registered only when {@code app.email.enabled}
 * is true (default). When disabled (e.g. in CI E2E runs), {@link NoOpEmailService}
 * is registered instead so the verification/reset token rows still get persisted
 * and the test-support controller can hand them to Playwright.
 */
@Service
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true", matchIfMissing = true)
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final Resend resend;
    private final boolean enabled;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailService(@Value("${resend.api-key}") String apiKey,
                        @Value("${app.email.enabled:true}") boolean enabled) {
        this.resend = new Resend(apiKey);
        this.enabled = enabled;
    }

    public void sendVerificationEmail(User user, String token) {
        // Route through the frontend SPA route (VerifyEmailPage) instead of
        // the raw backend JSON endpoint, so recipients land on a real page.
        String verifyLink = frontendUrl + "/verify-email?token=" + token;
        if (!enabled) {
            // Dev/manual-smoke escape hatch: log the link instead of calling
            // Resend, so a fresh user can verify by curl-ing the link from
            // the log output. Default is enabled=true, so prod is unchanged.
            log.warn("[email disabled] verify link for {} ({}): {}",
                    user.getEmail(), user.getId(), verifyLink);
            return;
        }
        String html = buildVerificationEmailHtml(user.getFirstName(), verifyLink);

        CreateEmailOptions request = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(user.getEmail())
                .subject("Verify your MentorNet account")
                .html(html)
                .build();

        try {
            resend.emails().send(request);
        } catch (ResendException e) {
            throw new EmailSendException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(User user, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        if (!enabled) {
            log.warn("[email disabled] password-reset link for {} ({}): {}",
                    user.getEmail(), user.getId(), resetLink);
            return;
        }
        String html = buildPasswordResetEmailHtml(user.getFirstName(), resetLink);

        CreateEmailOptions request = CreateEmailOptions.builder()
                .from(fromAddress)
                .to(user.getEmail())
                .subject("Reset your MentorNet password")
                .html(html)
                .build();

        try {
            resend.emails().send(request);
        } catch (ResendException e) {
            throw new EmailSendException("Failed to send password reset email", e);
        }
    }

    private String buildPasswordResetEmailHtml(String firstName, String resetLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td align="center" style="padding: 40px 0;">
                        <table width="600" cellpadding="0" cellspacing="0"
                               style="background-color: #ffffff; border-radius: 8px; padding: 40px;">
                          <tr>
                            <td>
                              <h2 style="color: #333333;">Reset your password, %s</h2>
                              <p style="color: #555555; font-size: 16px;">
                                We received a request to reset the password for your MentorNet account.
                                Click the button below to choose a new password. This link will expire in 1 hour.
                              </p>
                              <div style="text-align: center; margin: 32px 0;">
                                <a href="%s"
                                   style="background-color: #4F46E5; color: #ffffff; padding: 14px 28px;
                                          text-decoration: none; border-radius: 6px; font-size: 16px;
                                          font-weight: bold;">
                                  Reset Password
                                </a>
                              </div>
                              <p style="color: #888888; font-size: 13px;">
                                If the button doesn't work, copy and paste this link into your browser:<br/>
                                <a href="%s" style="color: #4F46E5;">%s</a>
                              </p>
                              <p style="color: #888888; font-size: 13px;">
                                If you did not request a password reset, you can safely ignore this email.
                                Your password will not be changed.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(firstName, resetLink, resetLink, resetLink);
    }

    private String buildVerificationEmailHtml(String firstName, String verifyLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 0;">
                  <table width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                      <td align="center" style="padding: 40px 0;">
                        <table width="600" cellpadding="0" cellspacing="0"
                               style="background-color: #ffffff; border-radius: 8px; padding: 40px;">
                          <tr>
                            <td>
                              <h2 style="color: #333333;">Welcome to MentorNet, %s!</h2>
                              <p style="color: #555555; font-size: 16px;">
                                Thanks for signing up. Please verify your email address to activate your account.
                                This link will expire in 24 hours.
                              </p>
                              <div style="text-align: center; margin: 32px 0;">
                                <a href="%s"
                                   style="background-color: #4F46E5; color: #ffffff; padding: 14px 28px;
                                          text-decoration: none; border-radius: 6px; font-size: 16px;
                                          font-weight: bold;">
                                  Verify Email Address
                                </a>
                              </div>
                              <p style="color: #888888; font-size: 13px;">
                                If the button doesn't work, copy and paste this link into your browser:<br/>
                                <a href="%s" style="color: #4F46E5;">%s</a>
                              </p>
                              <p style="color: #888888; font-size: 13px;">
                                If you did not create an account, you can safely ignore this email.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(firstName, verifyLink, verifyLink, verifyLink);
    }
}
