package com.xcy.internproject.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateUserRequest {

    @NotBlank(message = "username must not be blank")
    @Size(max = 50, message = "username must not exceed 50 characters")
    private String username;

    @NotBlank(message = "email must not be blank")
    @Email(message = "email must be valid")
    @Size(max = 100, message = "email must not exceed 100 characters")
    private String email;

    public CreateUserRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
