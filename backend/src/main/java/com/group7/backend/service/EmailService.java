package com.group7.backend.service;

import com.group7.backend.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(User user, String token) {
        String verifyLink = baseUrl + "/api/auth/verify-email?token=" + token;
        String html = buildVerificationEmailHtml(user.getFirstName(), verifyLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject("Verify your Group7 account");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public void sendPasswordResetEmail(User user, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;
        String html = buildPasswordResetEmailHtml(user.getFirstName(), resetLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject("Reset your Group7 password");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send password reset email", e);
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
                                We received a request to reset the password for your Group7 account.
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
                              <h2 style="color: #333333;">Welcome to Group7, %s!</h2>
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
