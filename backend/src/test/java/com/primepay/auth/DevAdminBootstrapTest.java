package com.primepay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DevAdminBootstrapTest {

    @Test
    void generatedPasswordsAreLongAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String password = DevAdminBootstrap.generatePassword();
            assertThat(password).hasSize(16);
            assertThat(password).containsPattern("[@#%&*!]");
            assertThat(password).containsPattern("[0-9]");
            assertThat(seen.add(password)).isTrue();
        }
    }
}
