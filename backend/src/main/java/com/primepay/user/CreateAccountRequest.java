package com.primepay.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+") String accountCode,
        @NotBlank @Size(max = 160) String displayName,
        @NotNull AccountRole role,
        Long parentId, // required for every role except Platform Admin (checked in AccountService)
        @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "[A-Za-z0-9._-]+") String username,
        @NotBlank @Size(min = 12, max = 200) String initialPassword) {
}
