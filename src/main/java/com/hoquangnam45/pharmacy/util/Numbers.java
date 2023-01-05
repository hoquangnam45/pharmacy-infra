package com.hoquangnam45.pharmacy.util;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public class Numbers {
  @Nullable public static Integer toInteger(String value, Integer defaultValue) {
    return Optional.ofNullable(value)
        .map(Integer::valueOf)
        .orElse(defaultValue);
  }
}
