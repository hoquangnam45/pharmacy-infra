package com.hoquangnam45.pharmacy.constant;

public enum EnvironmentE {
  DEV, PROD;

  public static EnvironmentE fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (EnvironmentE val : values()) {
      if (value.trim().equalsIgnoreCase(val.name())) {
        return val;
      }
    }
    throw new UnsupportedOperationException();
  }
}
