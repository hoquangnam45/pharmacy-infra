package com.hoquangnam45.pharmacy.infra;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.alpha.IHttpApi;
import software.amazon.awscdk.services.apigatewayv2.alpha.IVpcLink;
import software.amazon.awscdk.services.apigatewayv2.alpha.VpcLink;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.constructs.Construct;

@Getter
public class ApiGatewayNestedStack extends NestedStack {
  private final ISecurityGroup vpcLinkSg;
  private final IHttpApi httpApi;
  private final IVpcLink vpcLink;

  public ApiGatewayNestedStack(final Construct scope, final String id, final ApiGatewayNestedStackProps props) {
    super(scope, id, props);

    this.vpcLinkSg = createVpcLinkSecurityGroup("VpcLinkSg", props.getVpc());
    this.vpcLink = VpcLink.Builder.create(this, "VpcLink")
        .securityGroups(List.of(vpcLinkSg))
        .subnets(SubnetSelection.builder().subnets(props.getSelectedSubnets().getSubnets()).build())
        .vpc(props.getVpc())
        .build();

    this.httpApi = HttpApi.Builder.create(this, "HttpApi")
        .createDefaultStage(true)
        .build();
  }

  private ISecurityGroup createVpcLinkSecurityGroup(String id, IVpc vpc) {
    ISecurityGroup sg = SecurityGroup.Builder.create(this, id)
        .vpc(vpc)
        .build();
    sg.addIngressRule(Peer.anyIpv4(), Port.tcp(443));
    sg.addIngressRule(Peer.anyIpv6(), Port.tcp(443));
    return sg;
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class ApiGatewayNestedStackProps implements NestedStackProps {
    private final IVpc vpc;
    private final SelectedSubnets selectedSubnets;
  }
}
