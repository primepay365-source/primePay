package com.primepay.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Step 1 of sign-in: the account's email address or mobile number, and its password. */
public record LoginRequest(
        @NotBlank @Size(max = 160) @JsonAlias("username") String identifier,
        @NotBlank @Size(max = 200) String password) {
}
