package com.hoquangnam45.pharmacy.infra;

import com.hoquangnam45.pharmacy.infra.ApiGatewayNestedStack.ApiGatewayNestedStackProps;
import com.hoquangnam45.pharmacy.infra.EcsNestedStack.EcsNestedStackProps;
import com.hoquangnam45.pharmacy.infra.MqNestedStack.MqNestedStackProps;
import com.hoquangnam45.pharmacy.infra.CfNestedStack.CfNestedStackProps;
import com.hoquangnam45.pharmacy.infra.DbNestedStack.DbNestedStackProps;
import com.hoquangnam45.pharmacy.infra.VpcNestedStack.VpcNestedStackProps;
import com.hoquangnam45.pharmacy.util.Environments;

import lombok.Getter;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.amazonmq.CfnBroker.UserProperty;
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

    BucketNestedStack bucketStackDev = new BucketNestedStack(this, "S3StackDev", null);
    BucketNestedStack bucketStackProd = new BucketNestedStack(this, "S3StackProd", null);

    ApiGatewayNestedStack apiGwStackDev = new ApiGatewayNestedStack(this, "ApiGwStackDev",
        ApiGatewayNestedStackProps.builder()
            .vpc(vpcStack.getVpc())
            .selectedSubnets(vpcStack.getAppSubnets())
            .build());
    ApiGatewayNestedStack apiGwStackProd = new ApiGatewayNestedStack(this, "ApiGwStackProd",
        ApiGatewayNestedStackProps.builder()
            .vpc(vpcStack.getVpc())
            .selectedSubnets(vpcStack.getAppSubnets())
            .build());

    EcsNestedStack ecsStack = new EcsNestedStack(this, "EcsStack",
        EcsNestedStackProps.builder()
            .vpc(vpcStack.getVpc())
            .launchTemplateUserData("echo ECS_ENABLE_CONTAINER_METADATA=true >> /etc/ecs/ecs.config")
            .keypairName("pharmacy-ssh-keypair")
            .selectedSubnets(vpcStack.getAppSubnets())
            .clusterName("pharmacy-cluster")
            .build());

    CfNestedStack cfStackDev = new CfNestedStack(this, "CfStackDev",
        CfNestedStackProps.builder()
            .bucket(bucketStackDev.getBucket())
            .httpApi(apiGwStackDev.getHttpApi())
            .build());
    CfNestedStack cfStackProd = new CfNestedStack(this, "CfStackProd",
        CfNestedStackProps.builder()
            .bucket(bucketStackProd.getBucket())
            .httpApi(apiGwStackProd.getHttpApi())
            .build());

    String dbUsername = StringParameter.fromStringParameterName(this, "DbUsername", "DB_USERNAME").getStringValue();
    DbNestedStack dbStack = new DbNestedStack(this, "RdsStack", DbNestedStackProps.builder()
        .vpc(vpcStack.getVpc())
        .selectedSubnets(vpcStack.getDataSubnets())
        .credentials(Credentials.fromPassword(dbUsername, SecretValue.ssmSecure("DB_PASSWORD")))
        .build());

    String mqUsername = StringParameter.fromStringParameterName(this, "MqUsername", "MQ_USERNAME").getStringValue();
    String mqPassword = StringParameter.fromStringParameterName(this, "MqPassword", "MQ_PASSWORD").getStringValue();
    ;
    MqNestedStack mqStack = new MqNestedStack(this, "MqStack", MqNestedStackProps.builder()
        .vpc(vpcStack.getVpc())
        .selectedSubnets(vpcStack.getDataSubnets())
        .mqCredentials(UserProperty.builder()
            .username(mqUsername)
            .password(mqPassword)
            .build())
        .build());

    Environments.connect(apiGwStackDev.getVpcLinkSg(), ecsStack.getSg());
    Environments.connect(apiGwStackProd.getVpcLinkSg(), ecsStack.getSg());
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
    CfnOutput.Builder.create(this, "ApiGwDevId")
        .exportName(Environments.getOutputExportName(stackId, "ApiGwDevId"))
        .value(apiGwStackDev.getHttpApi().getApiId())
        .build();
    CfnOutput.Builder.create(this, "ApiGwProdId")
        .exportName(Environments.getOutputExportName(stackId, "ApiGwProdId"))
        .value(apiGwStackProd.getHttpApi().getApiId())
        .build();
    CfnOutput.Builder.create(this, "VpcLinkDevId")
        .exportName(Environments.getOutputExportName(stackId, "VpcLinkDevId"))
        .value(apiGwStackDev.getVpcLink().getVpcLinkId())
        .build();
    CfnOutput.Builder.create(this, "VpcLinkProdId")
        .exportName(Environments.getOutputExportName(stackId, "VpcLinkProdId"))
        .value(apiGwStackProd.getVpcLink().getVpcLinkId())
        .build();
    CfnOutput.Builder.create(this, "CfDistributionIdDev")
        .exportName(Environments.getOutputExportName(stackId, "CfDistributionIdDev"))
        .value(cfStackDev.getDistributionId())
        .build();
    CfnOutput.Builder.create(this, "CfDistributionIdProd")
        .exportName(Environments.getOutputExportName(stackId, "CfDistributionIdProd"))
        .value(cfStackProd.getDistributionId())
        .build();
  }
}