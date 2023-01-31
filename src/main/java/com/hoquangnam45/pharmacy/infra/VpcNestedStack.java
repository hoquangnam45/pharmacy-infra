package com.hoquangnam45.pharmacy.infra;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.constructs.Construct;

@Getter
public class VpcNestedStack extends NestedStack {
  private final IVpc vpc;
  private final SelectedSubnets appSubnets;
  private final SelectedSubnets dataSubnets;

  public VpcNestedStack(final Construct scope, final String id, final VpcNestedStackProps props) {
    super(scope, id, props);

    this.vpc = createVpc("Vpc", props.getAppSubnetName(), props.getDataSubnetName(), props.getVpcName());
    this.appSubnets = vpc.selectSubnets(SubnetSelection.builder()
        .subnetGroupName(props.getAppSubnetName())
        .build());
    this.dataSubnets = vpc.selectSubnets(SubnetSelection.builder()
        .subnetGroupName(props.getDataSubnetName())
        .build());
  }

  private IVpc createVpc(String id, String appSubnetName, String dataSubnetName, String vpcName) {
    SubnetConfiguration appSubnetConfiguration = SubnetConfiguration.builder()
        .cidrMask(28)
        .subnetType(SubnetType.PUBLIC)
        .name(appSubnetName)
        .mapPublicIpOnLaunch(true)
        .build();
    SubnetConfiguration dataSubnetConfiguration = SubnetConfiguration.builder()
        .cidrMask(28)
        .subnetType(SubnetType.PRIVATE_ISOLATED)
        .name(dataSubnetName)
        .build();
    List<SubnetConfiguration> subnets = List.of(appSubnetConfiguration, dataSubnetConfiguration);
    return Vpc.Builder.create(this, id)
        .enableDnsHostnames(true)
        .enableDnsSupport(true)
        .subnetConfiguration(subnets)
        .vpcName(vpcName)
        .maxAzs(3)
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class VpcNestedStackProps implements NestedStackProps {
    private final String appSubnetName;
    private final String dataSubnetName;
    private final String vpcName;
  }
}
