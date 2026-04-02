package com.example.purchaserequest.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * パスワードハッシュ生成ユーティリティ
 */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "password";
        String existingHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        
        System.out.println("Testing password: " + password);
        System.out.println("Existing hash: " + existingHash);
        System.out.println("Password matches existing hash: " + encoder.matches(password, existingHash));
        System.out.println("\nGenerating new hash for 'password':");
        System.out.println(encoder.encode(password));
    }
}

// Made with Bob
