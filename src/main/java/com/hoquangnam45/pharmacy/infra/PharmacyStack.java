package com.hoquangnam45.pharmacy.infra;

import com.hoquangnam45.pharmacy.infra.EcsNestedStack.EcsNestedStackProps;
import com.hoquangnam45.pharmacy.infra.LbNestedStack.LbNestedStackProps;
import com.hoquangnam45.pharmacy.infra.MqNestedStack.MqNestedStackProps;

import java.util.List;

import com.hoquangnam45.pharmacy.infra.BucketNestedStack.BucketNestedStackProps;
import com.hoquangnam45.pharmacy.infra.DbNestedStack.DbNestedStackProps;
import com.hoquangnam45.pharmacy.infra.VpcNestedStack.VpcNestedStackProps;
import com.hoquangnam45.pharmacy.util.Environments;

import lombok.Getter;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.amazonmq.CfnBroker.UserProperty;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.iam.AnyPrincipal;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.constructs.Construct;

@Getter
public class PharmacyStack extends Stack {
  public PharmacyStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);

    VpcNestedStack vpcStack = new VpcNestedStack(this, "VpcStack",
        VpcNestedStackProps.builder()
            .appSubnetName("App")
            .dataSubnetName("Data")
            .build());

    PolicyStatement policyStatement = PolicyStatement.Builder.create()
        .actions(List.of("s3:GetObject"))
        .principals(List.of(new AnyPrincipal()))
        .resources(List.of("*"))
        .build();
    BucketNestedStack bucketStackDev = new BucketNestedStack(this, "S3StackDev", BucketNestedStackProps.builder()
        .policyStatement(policyStatement)
        .build());
    BucketNestedStack bucketStackProd = new BucketNestedStack(this, "S3StackProd", BucketNestedStackProps.builder()
        .policyStatement(policyStatement)
        .build());

    LbNestedStack lbStack = new LbNestedStack(this, "LbStack", LbNestedStackProps.builder()
        .vpc(vpcStack.getVpc())
        .selectedSubnets(vpcStack.getAppSubnets())
        .build());

    EcsNestedStack ecsStack = new EcsNestedStack(this, "EcsStack",
        EcsNestedStackProps.builder()
            .vpc(vpcStack.getVpc())
            .launchTemplateUserData("")
            .keypairName("pharmacy-ssh-keypair")
            .selectedSubnets(vpcStack.getAppSubnets())
            .clusterName("pharmacy-cluster")
            .build());

    String dbUsername = StringParameter.fromStringParameterName(this, "DbUsername", "DB_USERNAME").getStringValue();
    DbNestedStack dbStack = new DbNestedStack(this, "RdsStack", DbNestedStackProps.builder()
        .vpc(vpcStack.getVpc())
        .selectedSubnets(vpcStack.getDataSubnets())
        .credentials(Credentials.fromPassword(dbUsername, SecretValue.ssmSecure("DB_PASSWORD")))
        .build());

    String mqUsername = StringParameter.fromStringParameterName(this, "MqUsername", "MQ_USERNAME").getStringValue();
    String mqPassword = StringParameter.fromStringParameterName(this, "MqPassword", "MQ_PASSWORD").getStringValue();

    MqNestedStack mqStack = new MqNestedStack(this, "MqStack", MqNestedStackProps.builder()
        .vpc(vpcStack.getVpc())
        .selectedSubnets(vpcStack.getDataSubnets())
        .mqCredentials(UserProperty.builder()
            .username(mqUsername)
            .password(mqPassword)
            .build())
        .build());

    lbStack.getSg().addIngressRule(Peer.anyIpv4(), Port.tcp(80));
    lbStack.getSg().addIngressRule(Peer.anyIpv6(), Port.tcp(80));

    Environments.connect(lbStack.getSg(), ecsStack.getSg());
    Environments.connect(ecsStack.getSg(), dbStack.getSg());
    Environments.connect(ecsStack.getSg(), ecsStack.getSg());
    Environments.connect(ecsStack.getSg(), mqStack.getSg());

    String stackId = getArtifactId();
    CfnOutput.Builder.create(this, "S3BucketProdName")
        .exportName(Environments.getOutputExportName(stackId, "S3BucketProdName"))
        .value(bucketStackProd.getBucket().getBucketName())
        .build();
    CfnOutput.Builder.create(this, "S3BucketDevName")
        .exportName(Environments.getOutputExportName(stackId, "S3BucketDevName"))
        .value(bucketStackDev.getBucket().getBucketName())
        .build();
    CfnOutput.Builder.create(this, "ClusterName")
        .exportName(Environments.getOutputExportName(stackId, "ClusterName"))
        .value(ecsStack.getCluster().getClusterName())
        .build();
    CfnOutput.Builder.create(this, "ClusterCapacityProviderName")
        .exportName(Environments.getOutputExportName(stackId, "ClusterCapacityProviderName"))
        .value(ecsStack.getAsgCapacityProvider().getCapacityProviderName())
        .build();
    CfnOutput.Builder.create(this, "ClusterNamespaceName")
        .exportName(Environments.getOutputExportName(stackId, "ClusterNamespaceName"))
        .value(ecsStack.getNamespace().getNamespaceName())
        .build();
    CfnOutput.Builder.create(this, "ClusterNamespaceArn")
        .exportName(Environments.getOutputExportName(stackId, "ClusterNamespaceArn"))
        .value(ecsStack.getNamespace().getNamespaceArn())
        .build();
    CfnOutput.Builder.create(this, "ClusterNamespaceId")
        .exportName(Environments.getOutputExportName(stackId, "ClusterNamespaceId"))
        .value(ecsStack.getNamespace().getNamespaceId())
        .build();
    CfnOutput.Builder.create(this, "VpcId")
        .exportName(Environments.getOutputExportName(stackId, "VpcId"))
        .value(vpcStack.getVpc().getVpcId())
        .build();
  }
}