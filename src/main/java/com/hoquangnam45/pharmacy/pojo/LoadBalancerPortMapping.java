package com.hoquangnam45.pharmacy.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LoadBalancerPortMapping {
  private final Integer listenerPort;
  private final Integer targetPort;
  private final String healthCheckPath;
  private final Boolean isPublic;
}
