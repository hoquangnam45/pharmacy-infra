package com.hoquangnam45.pharmacy.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hoquangnam45.pharmacy.pojo.LoadBalancerPortMapping;

import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;

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

  public static List<LoadBalancerPortMapping> parseLbPortMapping(String exportedValues) {
    return Optional.ofNullable(exportedValues)
        .map(str -> str.trim())
        .filter(str -> !str.isEmpty())
        .map(str -> Arrays.asList(str.split(",")).stream())
        .map(portMappings -> {
          return portMappings
              .map(portMapping -> portMapping.trim())
              .map(portMapping -> {
                List<String> tokens = Arrays.asList(portMapping.split(":", 4));
                return LoadBalancerPortMapping.builder()
                    .healthCheckPath(tokens.get(3).trim())
                    .listenerPort(Integer.parseInt(tokens.get(0).trim()))
                    .targetPort(Integer.parseInt(tokens.get(1).trim()))
                    .isPublic(Boolean.parseBoolean(tokens.get(2)))
                    .build();
              })
              .collect(Collectors.toList());
        })
        .orElse(List.of());
  }

  public static List<PortMapping> parseContainerPortMapping(String portMappingsStr) {
    return Optional.ofNullable(portMappingsStr)
        .map(str -> str.trim())
        .filter(str -> !str.isEmpty())
        .map(str -> Arrays.asList(str.split(",")).stream())
        .map(portMappings -> {
          return portMappings
              .map(portMapping -> portMapping.trim())
              .map(portMapping -> {
                List<String> envTokens = Arrays.asList(portMapping.split(":", 3));
                return PortMapping.builder()
                    .hostPort(Integer.parseInt(envTokens.get(0).trim()))
                    .containerPort(Integer.parseInt(envTokens.get(1).trim()))
                    .protocol(
                        envTokens.size() > 2 && "udp".equalsIgnoreCase(envTokens.get(2)) ? Protocol.UDP : Protocol.TCP)
                    .build();
              })
              .collect(Collectors.toList());
        })
        .orElse(List.of());
  }

  public static String getOutputExportName(String outputStack, String exportName) {
    return outputStack + ":" + exportName;
  }

  public static void connect(ISecurityGroup from, ISecurityGroup to) {
    to.addIngressRule(from, Port.allTraffic());
  }
}
