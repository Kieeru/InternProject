package com.xcy.internproject.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, CharSequence> {

    private int max;

    @Override
    public void initialize(Utf8ByteLength constraintAnnotation) {
        max = constraintAnnotation.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
