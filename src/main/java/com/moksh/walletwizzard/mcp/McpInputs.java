package com.moksh.walletwizzard.mcp;

import com.moksh.walletwizzard.exception.InvalidInputException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public final class McpInputs {

    private McpInputs() {}

    public static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }

    public static LocalDate parseDate(String value, String field) {
        String v = blankToNull(value);
        if (v == null) return null;
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(
                    "Invalid date '" + v + "' for '" + field + "'. Use YYYY-MM-DD format (e.g. 2026-07-15).");
        }
    }

    public static UUID parseUuid(String value, String field) {
        String v = blankToNull(value);
        if (v == null) return null;
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException(
                    "Invalid UUID '" + v + "' for '" + field + "'.");
        }
    }

    public static BigDecimal parseAmount(String value, String field) {
        String v = blankToNull(value);
        if (v == null) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new InvalidInputException(
                    "Invalid amount '" + v + "' for '" + field + "'. Use decimal notation e.g. '1000.00'.");
        }
    }

    public static <E extends Enum<E>> E parseEnum(String value, String field, Class<E> enumClass) {
        String v = blankToNull(value);
        if (v == null) return null;
        try {
            return Enum.valueOf(enumClass, v.toUpperCase());
        } catch (IllegalArgumentException e) {
            String valid = Arrays.stream(enumClass.getEnumConstants())
                    .map(Enum::name).collect(Collectors.joining(" | "));
            throw new InvalidInputException(
                    "Invalid value '" + v + "' for '" + field + "'. Valid values: " + valid + ".");
        }
    }

    // ── Required-field variants (throw when blank/null) ──────────────────────

    public static UUID requireUuid(String value, String field) {
        UUID result = parseUuid(value, field);
        if (result == null) throw new InvalidInputException("Field '" + field + "' is required.");
        return result;
    }

    public static LocalDate requireDate(String value, String field) {
        LocalDate result = parseDate(value, field);
        if (result == null) throw new InvalidInputException("Field '" + field + "' is required.");
        return result;
    }

    public static BigDecimal requireAmount(String value, String field) {
        BigDecimal result = parseAmount(value, field);
        if (result == null) throw new InvalidInputException("Field '" + field + "' is required.");
        return result;
    }

    public static <E extends Enum<E>> E requireEnum(String value, String field, Class<E> enumClass) {
        E result = parseEnum(value, field, enumClass);
        if (result == null) throw new InvalidInputException("Field '" + field + "' is required.");
        return result;
    }
}
