package com.tars.spotai.service;

/**
 * Abstraction for sending SMS verification codes.
 * Different implementations can target different SMS providers (e.g. log, Aliyun, Twilio).
 */
public interface SmsService {
    /**
     * Sends the verification code to the given phone number.
     */
    void sendCode(String phone, String code);
}
