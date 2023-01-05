package com.hoquangnam45.pharmacy.util;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.Port;

public class Environments {
  public static Map<String, String> collect(String exportedValues) {
    return Optional.ofNullable(exportedValues)
        .map(str -> str.trim())
        .filter(str -> !str.isEmpty())
        .map(str -> Arrays.asList(str.split(",")).stream())
        .map(stream -> {
          return stream
              .map(token -> token.trim())
              .filter(token -> !token.equals(""))
              .collect(Collectors.toMap(token -> token, token -> System.getenv(token)));
        })
        .orElse(Map.of());
  }

  public static String getOutputExportName(String outputStack, String exportName) {
    return outputStack + ":" + exportName;
  }

  public static void connect(ISecurityGroup from, ISecurityGroup to) {
    to.addIngressRule(from, Port.allTraffic());
  }
}
