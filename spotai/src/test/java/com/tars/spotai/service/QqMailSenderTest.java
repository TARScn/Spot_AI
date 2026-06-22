package com.tars.spotai.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

class QqMailSenderTest {
    private static final String FROM = "spotai@qq.com";
    private static final String TO = "18479867632@163.com";
    private static final String AUTH_CODE_ENV = "QQ_SMTP_AUTH_CODE";

    @Test
    @EnabledIfEnvironmentVariable(named = AUTH_CODE_ENV, matches = ".+")
    void sendTestMailByQqSmtp() throws Exception {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.qq.com");
        mailSender.setPort(465);
        mailSender.setUsername(FROM);
        mailSender.setPassword(System.getenv(AUTH_CODE_ENV));
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(FROM);
        helper.setTo(TO);
        helper.setSubject("Spot AI 邮件发送测试");
        helper.setText("""
                你好，这是一封来自 Spot AI 项目的 QQ SMTP 测试邮件。

                如果你收到这封邮件，说明 QQ 邮箱 SMTP 授权码和发送链路配置可用。
                """, false);

        mailSender.send(message);
    }
}
