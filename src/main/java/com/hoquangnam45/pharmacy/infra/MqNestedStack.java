package com.hoquangnam45.pharmacy.infra;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.amazonmq.CfnBroker;
import software.amazon.awscdk.services.amazonmq.CfnBroker.LogListProperty;
import software.amazon.awscdk.services.amazonmq.CfnBroker.UserProperty;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.constructs.Construct;

@Getter
public class MqNestedStack extends NestedStack {
  private final ISecurityGroup sg;

  public MqNestedStack(final Construct scope, final String id, final MqNestedStackProps props) {
    super(scope, id, props);
    this.sg = createMqSecurityGroup("Sg", props.getVpc());
    CfnBroker.Builder.create(this, "MqBroker")
        .autoMinorVersionUpgrade(true)
        .brokerName(getArtifactId() + "Broker")
        .deploymentMode("SINGLE_INSTANCE")
        .engineType("RABBITMQ")
        .engineVersion("3.10.10")
        .hostInstanceType("mq.t3.micro")
        .publiclyAccessible(false)
        .users(List.of(props.getMqCredentials()))
        .securityGroups(List.of(sg.getSecurityGroupId()))
        .subnetIds(List.of(props.getSelectedSubnets().getSubnetIds().get(0)))
        .logs(LogListProperty.builder()
            .general(true)
            .build())
        .build();
  }

  private ISecurityGroup createMqSecurityGroup(String id, IVpc vpc) {
    return SecurityGroup.Builder.create(this, id)
        .vpc(vpc)
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class MqNestedStackProps implements NestedStackProps {
    private final IVpc vpc;
    private final SelectedSubnets selectedSubnets;
    private final UserProperty mqCredentials;
  }
}
