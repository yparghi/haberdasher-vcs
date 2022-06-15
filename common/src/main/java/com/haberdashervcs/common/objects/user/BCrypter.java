package com.haberdashervcs.common.objects.user;

import org.springframework.security.crypto.bcrypt.BCrypt;


public final class BCrypter {

    private BCrypter() {
        throw new UnsupportedOperationException();
    }


    public static String bcryptPassword(String plaintextPassword) {
        String salt = BCrypt.gensalt(12);
        String hashed = BCrypt.hashpw(plaintextPassword, salt);
        return hashed;
    }

    public static boolean passwordMatches(String plaintextPassword, String bcryptedPassword) {
        return BCrypt.checkpw(plaintextPassword, bcryptedPassword);
    }
}
