package com.hoquangnam45.pharmacy.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.hoquangnam45.pharmacy.constant.EnvironmentE;
import com.hoquangnam45.pharmacy.util.Environments;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpApiAttributes;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpMethod;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpRoute;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpRouteKey;
import software.amazon.awscdk.services.apigatewayv2.alpha.IHttpApi;
import software.amazon.awscdk.services.apigatewayv2.alpha.IVpcLink;
import software.amazon.awscdk.services.apigatewayv2.alpha.MappingValue;
import software.amazon.awscdk.services.apigatewayv2.alpha.ParameterMapping;
import software.amazon.awscdk.services.apigatewayv2.alpha.VpcLink;
import software.amazon.awscdk.services.apigatewayv2.alpha.VpcLinkAttributes;
import software.amazon.awscdk.services.apigatewayv2.integrations.alpha.HttpServiceDiscoveryIntegration;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcAttributes;
import software.amazon.awscdk.services.ecs.AppProtocol;
import software.amazon.awscdk.services.ecs.AssociateCloudMapServiceOptions;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.BaseService;
import software.amazon.awscdk.services.ecs.CapacityProviderStrategy;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterAttributes;
import software.amazon.awscdk.services.ecs.Compatibility;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.Ec2Service;
import software.amazon.awscdk.services.ecs.HealthCheck;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.RepositoryImageProps;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.servicediscovery.DnsRecordType;
import software.amazon.awscdk.services.servicediscovery.IPrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespaceAttributes;
import software.amazon.awscdk.services.servicediscovery.Service;
import software.constructs.Construct;

public class EcsServiceDeploymentStack extends Stack {
  public EcsServiceDeploymentStack(final Construct scope, final String id, final EcsServiceDeploymentStackProps props) {
    super(scope, id, props);

    String stackId = props.getOutputStack();
    IVpc vpc = Vpc.fromVpcAttributes(this, "Vpc", VpcAttributes.builder()
        .vpcId(Fn.importValue(Environments.getOutputExportName(stackId, "VpcId")))
        .availabilityZones(getAvailabilityZones())
        .build());
    ICluster cluster = Cluster.fromClusterAttributes(this, "Cluster", ClusterAttributes.builder()
        .clusterName(Fn.importValue(Environments.getOutputExportName(stackId, "ClusterName")))
        .securityGroups(new ArrayList<>())
        .vpc(vpc)
        .build());
    IPrivateDnsNamespace namespace = PrivateDnsNamespace.fromPrivateDnsNamespaceAttributes(this, "PrivateDnsNamespace",
        PrivateDnsNamespaceAttributes.builder()
            .namespaceArn(Fn.importValue(Environments.getOutputExportName(stackId, "ClusterNamespaceArn")))
            .namespaceId(Fn.importValue(Environments.getOutputExportName(stackId, "ClusterNamespaceId")))
            .namespaceName(Fn.importValue(Environments.getOutputExportName(stackId, "ClusterNamespaceName")))
            .build());

    TaskDefinition td = createTaskDefinition("Td",
        props.getServiceName(),
        props.getFamily(),
        props.getImageName(),
        props.getContainerName(),
        props.getContainerPort(),
        props.getHostPort(),
        props.getHealthCheckCommand(),
        Environments.collect(props.getExportedEnvironments()),
        props.getMemoryReservationMiB(),
        getSecret("Secret", props.getRegistryCredentialSecret()));

    Ec2Service service = createService(
        "Service",
        cluster,
        td,
        Fn.importValue(Environments.getOutputExportName(stackId, "ClusterCapacityProviderName")),
        props.getServiceName(),
        namespace);
    if (props.getIsPublic()) {
      EnvironmentE environment = EnvironmentE.fromValue(props.getEnvironment());
      IVpcLink vpcLink = null;
      IHttpApi httpApi = null;
      switch (environment) {
        case DEV:
          vpcLink = VpcLink.fromVpcLinkAttributes(this, "VpcLink", VpcLinkAttributes.builder()
              .vpc(vpc)
              .vpcLinkId(Fn.importValue(Environments.getOutputExportName(stackId, "VpcLinkDevId")))
              .build());
          httpApi = HttpApi.fromHttpApiAttributes(this, "HttpApi", HttpApiAttributes.builder()
              .httpApiId(Fn.importValue(Environments.getOutputExportName(stackId, "ApiGwDevId")))
              .build());
          break;
        case PROD:
          vpcLink = VpcLink.fromVpcLinkAttributes(this, "VpcLink", VpcLinkAttributes.builder()
              .vpc(vpc)
              .vpcLinkId(Fn.importValue(Environments.getOutputExportName(stackId, "VpcLinkProdId")))
              .build());
          httpApi = HttpApi.fromHttpApiAttributes(this, "HttpApi", HttpApiAttributes.builder()
              .httpApiId(Fn.importValue(Environments.getOutputExportName(stackId, "ApiGwProdId")))
              .build());
          break;
        default:
          throw new UnsupportedOperationException();
      }
      integrateWithApiGw("ApiGwIntegration", httpApi, vpcLink, service, props.getApiGwConnectProps().getRouteKey());
    }
  }

  private void integrateWithApiGw(
      String id,
      IHttpApi httpApi,
      IVpcLink vpcLink,
      BaseService service,
      String routeKey) {
    HttpServiceDiscoveryIntegration httpIntegration = HttpServiceDiscoveryIntegration.Builder
        .create(id, service.getCloudMapService())
        .method(HttpMethod.ANY)
        .vpcLink(vpcLink)
        .parameterMapping(new ParameterMapping().overwritePath(MappingValue.custom("/${request.path.proxy}")))
        .build();
    HttpRoute.Builder.create(this, id + "Route")
        .httpApi(httpApi)
        .integration(httpIntegration)
        .routeKey(HttpRouteKey.with(routeKey, HttpMethod.ANY))
        .build();
  }

  private Ec2Service createService(
      String id,
      ICluster cluster,
      TaskDefinition td,
      String capacityProviderName,
      String serviceName,
      IPrivateDnsNamespace namespace) {
    CapacityProviderStrategy defaultStrategy = CapacityProviderStrategy.builder()
        .capacityProvider(capacityProviderName)
        .weight(100)
        .build();
    Ec2Service ec2Service = Ec2Service.Builder.create(this, id)
        .capacityProviderStrategies(List.of(defaultStrategy))
        .cluster(cluster)
        .serviceName(serviceName)
        .taskDefinition(td)
        .build();
    Service discoveryService = Service.Builder.create(this, "DiscoveryService")
        .namespace(namespace)
        .dnsRecordType(DnsRecordType.SRV)
        .name(serviceName)
        .build();
    ec2Service.associateCloudMapService(AssociateCloudMapServiceOptions.builder()
        .container(td.getDefaultContainer())
        .containerPort(td.getDefaultContainer().getContainerPort())
        .service(discoveryService)
        .build());
    return ec2Service;
  }

  private TaskDefinition createTaskDefinition(
      String id,
      String serviceName,
      String family,
      String imageName,
      String containerName,
      Integer containerPort,
      Integer hostPort,
      String healthCheckCommand,
      Map<String, String> environments,
      Integer memoryReservationMib,
      ISecret registryCredential) {
    TaskDefinition td = TaskDefinition.Builder.create(this, id)
        .compatibility(Compatibility.EC2)
        .family(family)
        .build();
    ContainerImage image = ContainerImage.fromRegistry(imageName,
        RepositoryImageProps.builder()
            .credentials(registryCredential)
            .build());
    PortMapping portMapping = PortMapping.builder()
        .containerPort(containerPort)
        .hostPort(hostPort)
        .name(containerName)
        .appProtocol(AppProtocol.getHttp())
        .build();
    HealthCheck healthCheck = HealthCheck.builder().command(List.of(healthCheckCommand)).build();
    td.addContainer(id + "Container", ContainerDefinitionOptions.builder()
        .environment(environments)
        .image(image)
        .memoryReservationMiB(memoryReservationMib)
        .portMappings(List.of(portMapping))
        .containerName(containerName)
        .logging(LogDriver.awsLogs(
            AwsLogDriverProps.builder()
                .streamPrefix(getArtifactId() + "Log")
                .build()))
        .healthCheck(healthCheck)
        .build());
    return td;
  }

  public ISecret getSecret(String id, String secretName) {
    if (secretName == null || secretName.trim().isEmpty()) {
      return null;
    }
    return Secret.fromSecretNameV2(this, id, secretName);
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class EcsServiceDeploymentStackProps implements StackProps {
    private final String family;
    private final String containerName;
    private final String exportedEnvironments;
    private final String imageName;
    private final String registryCredentialSecret;
    private final Integer memoryReservationMiB;
    private final Integer containerPort;
    private final Integer hostPort;
    private final String serviceName;
    private final Boolean isPublic;
    private final ApiGwConnectProps apiGwConnectProps;
    private final String outputStack;
    private final String environment;
    private final String healthCheckCommand;
    private final Environment env;
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class ApiGwConnectProps {
    private final String routeKey;
  }
}
