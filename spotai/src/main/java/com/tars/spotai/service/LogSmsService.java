package com.tars.spotai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Log-based implementation of {@link SmsService}.
 * Instead of sending a real SMS, it logs the verification code — suitable for development and testing.
 */
@Service
public class LogSmsService implements SmsService {
    private static final Logger log = LoggerFactory.getLogger(LogSmsService.class);

    /* 将验证码输出到日志（开发/测试环境替代真实短信发送） */
    @Override
    public void sendCode(String phone, String code) {
        log.info("SpotAI login code for phone {} is {}", phone, code);
    }
}
