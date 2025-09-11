package com.example.eternaltalk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank String nickname,
            @NotBlank String password
    ) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record JwtResponse(String accessToken, String refreshToken) {}

    public record UpdateProfileRequest(@NotBlank String nickname) {}

    public record ConsentRequest(boolean consent) {}
}
