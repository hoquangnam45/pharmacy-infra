package com.hoquangnam45.pharmacy.infra;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.constructs.Construct;

@Getter
public class LbNestedStack extends NestedStack {
  private static final String LB = "Alb";

  private final ISecurityGroup sg;
  private final IApplicationLoadBalancer lb;

  public LbNestedStack(final Construct scope, final String id, final LbNestedStackProps props) {
    super(scope, id, props);
    IVpc vpc = props.getVpc();
    this.sg = createLoadBalancerSecurityGroup(LB + "Sg", vpc);
    this.lb = createLoadBalancer(LB, vpc, sg, props.getSelectedSubnets());
  }

  private ISecurityGroup createLoadBalancerSecurityGroup(String id, IVpc vpc) {
    return SecurityGroup.Builder.create(this, id)
        .vpc(vpc)
        .build();
  }

  private IApplicationLoadBalancer createLoadBalancer(String id, IVpc vpc,
      ISecurityGroup securityGroup, SelectedSubnets selectedSubnets) {
    return ApplicationLoadBalancer.Builder.create(this, id)
        .securityGroup(securityGroup)
        .vpc(vpc)
        .internetFacing(true)
        .vpcSubnets(SubnetSelection.builder().subnets(selectedSubnets.getSubnets()).build())
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class LbNestedStackProps implements NestedStackProps {
    private final IVpc vpc;
    private final SelectedSubnets selectedSubnets;
  }
}
