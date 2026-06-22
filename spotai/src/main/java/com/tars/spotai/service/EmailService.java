package com.tars.spotai.service;

/**
 * Abstraction for sending email verification codes.
 */
public interface EmailService {
    void sendCode(String email, String code);
}
