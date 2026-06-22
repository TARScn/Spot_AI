package com.tars.spotai.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends login verification codes through the configured SMTP account.
 */
@Service
public class MailEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final String from;

    public MailEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendCode(String email, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Spot AI 登录验证码");
            helper.setText("""
                    你的 Spot AI 登录验证码是：%s

                    验证码 5 分钟内有效。如果不是你本人操作，请忽略这封邮件。
                    """.formatted(code), false);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("邮件验证码发送失败", e);
        }
    }
}
