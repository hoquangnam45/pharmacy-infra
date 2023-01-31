package com.hoquangnam45.pharmacy.deployment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.hoquangnam45.pharmacy.util.Environments;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpMethod;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpRoute;
import software.amazon.awscdk.services.apigatewayv2.alpha.HttpRouteKey;
import software.amazon.awscdk.services.apigatewayv2.alpha.IHttpApi;
import software.amazon.awscdk.services.apigatewayv2.alpha.IVpcLink;
import software.amazon.awscdk.services.apigatewayv2.alpha.MappingValue;
import software.amazon.awscdk.services.apigatewayv2.alpha.ParameterMapping;
import software.amazon.awscdk.services.apigatewayv2.integrations.alpha.HttpServiceDiscoveryIntegration;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetFilter;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
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
import software.amazon.awscdk.services.ecs.NetworkMode;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.RepositoryImageProps;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.servicediscovery.DnsRecordType;
import software.amazon.awscdk.services.servicediscovery.IPrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespaceAttributes;
import software.amazon.awscdk.services.servicediscovery.RoutingPolicy;
import software.amazon.awscdk.services.servicediscovery.Service;
import software.constructs.Construct;

public class EcsServiceDeploymentStack extends Stack {
  public EcsServiceDeploymentStack(final Construct scope, final String id, final EcsServiceDeploymentStackProps props) {
    super(scope, id, props);

    String stackId = props.getOutputStack();
    IVpc vpc = Vpc.fromLookup(this, "Vpc", VpcLookupOptions.builder().vpcName(props.getVpcName()).build());
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
    SubnetSelection ecsSubnetSelection = null;
    ISecurityGroup ecsServiceSg = null;
    if (props.getIsBootstrap()) {
      ecsServiceSg = SecurityGroup.fromSecurityGroupId(this, "AppSg",
          Fn.importValue(Environments.getOutputExportName(stackId, "AppSg")));
      List<String> appSubnetIds = Fn.split(",",
          Fn.importValue(Environments.getOutputExportName(stackId, "AppSubnetIds")), 3);
      ecsSubnetSelection = SubnetSelection.builder()
          .availabilityZones(vpc.getAvailabilityZones())
          .subnetFilters(List.of(SubnetFilter.byIds(appSubnetIds)))
          .subnets(vpc.getPublicSubnets())
          .build();
    }

    TaskDefinition td = createTaskDefinition("Td",
        props.getServiceName(),
        props.getFamily(),
        props.getImageName(),
        props.getContainerName(),
        toPortMappings(props.getPortMappings()),
        props.getHealthCheckCommand(),
        Environments.collect(props.getExportedEnvironments()),
        props.getMemoryReservationMiB(),
        getSecret("Secret", props.getRegistryCredentialSecret()),
        props.getIsBootstrap());

    createService(
        "Service",
        cluster,
        td,
        Fn.importValue(Environments.getOutputExportName(stackId, "ClusterCapacityProviderName")),
        props.getServiceName(),
        namespace,
        ecsServiceSg,
        ecsSubnetSelection,
        props.getIsBootstrap());
    if (props.getIsPublic()) {
      // TODO: Find another approach
      // EnvironmentE environment = EnvironmentE.fromValue(props.getEnvironment());
      // IVpcLink vpcLink = null;
      // IHttpApi httpApi = null;
      // switch (environment) {
      // case DEV:
      // vpcLink = VpcLink.fromVpcLinkAttributes(this, "VpcLink",
      // VpcLinkAttributes.builder()
      // .vpc(vpc)
      // .vpcLinkId(Fn.importValue(Environments.getOutputExportName(stackId,
      // "VpcLinkDevId")))
      // .build());
      // httpApi = HttpApi.fromHttpApiAttributes(this, "HttpApi",
      // HttpApiAttributes.builder()
      // .httpApiId(Fn.importValue(Environments.getOutputExportName(stackId,
      // "ApiGwDevId")))
      // .build());
      // break;
      // case PROD:
      // vpcLink = VpcLink.fromVpcLinkAttributes(this, "VpcLink",
      // VpcLinkAttributes.builder()
      // .vpc(vpc)
      // .vpcLinkId(Fn.importValue(Environments.getOutputExportName(stackId,
      // "VpcLinkProdId")))
      // .build());
      // httpApi = HttpApi.fromHttpApiAttributes(this, "HttpApi",
      // HttpApiAttributes.builder()
      // .httpApiId(Fn.importValue(Environments.getOutputExportName(stackId,
      // "ApiGwProdId")))
      // .build());
      // break;
      // default:
      // throw new UnsupportedOperationException();
      // }
      // integrateWithApiGw("ApiGwIntegration", httpApi, vpcLink, service,
      // props.getRouteKey());
    }
  }

  private List<PortMapping> toPortMappings(String portMappingsStr) {
    String[] parts = portMappingsStr.trim().split(",");
    List<PortMapping> ports = new ArrayList<>();
    for (String portMappingStr : parts) {
      String[] portParts = portMappingStr.trim().split(":", 4);
      Integer hostPort = Integer.valueOf(portParts[0]);
      Integer containerPort = Integer.valueOf(portParts[1]);
      String name = null;
      AppProtocol appProtocol = null;
      if (portParts.length > 2) {
        name = portParts[2];
        switch (portParts[3].toLowerCase()) {
          case "http":
            appProtocol = AppProtocol.getHttp();
            break;
          case "http2":
            appProtocol = AppProtocol.getHttp2();
            break;
          case "grpc":
            appProtocol = AppProtocol.getGrpc();
            break;
          default:
            break;
        }
      }
      PortMapping portMapping = PortMapping.builder()
          .containerPort(containerPort)
          .hostPort(hostPort)
          .name(name)
          .appProtocol(appProtocol)
          .build();
      ports.add(portMapping);
    }
    return Collections.unmodifiableList(ports);
  }

  // TODO: Use another approach with cheaper cost
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
      IPrivateDnsNamespace namespace,
      ISecurityGroup sg,
      SubnetSelection subnetSelection,
      Boolean bootstrap) {
    CapacityProviderStrategy defaultStrategy = CapacityProviderStrategy.builder()
        .capacityProvider(capacityProviderName)
        .weight(100)
        .build();
    Ec2Service ec2Service = Ec2Service.Builder.create(this, id)
        .capacityProviderStrategies(List.of(defaultStrategy))
        .cluster(cluster)
        .serviceName(serviceName)
        .taskDefinition(td)
        .assignPublicIp(false)
        .securityGroups(bootstrap ? List.of(sg) : null)
        .vpcSubnets(bootstrap ? subnetSelection : null)
        .build();
    DnsRecordType dnsRecordType = bootstrap ? DnsRecordType.A : DnsRecordType.SRV;
    Service discoveryService = Service.Builder.create(this, "DiscoveryService")
        .namespace(namespace)
        .dnsRecordType(dnsRecordType)
        .name(serviceName)
        .routingPolicy(RoutingPolicy.MULTIVALUE)
        .build();
    ec2Service.associateCloudMapService(AssociateCloudMapServiceOptions.builder()
        .container(dnsRecordType == DnsRecordType.SRV ? td.getDefaultContainer() : null)
        .containerPort(dnsRecordType == DnsRecordType.SRV ? td.getDefaultContainer().getContainerPort() : null)
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
      List<PortMapping> portMappings,
      String healthCheckCommand,
      Map<String, String> environments,
      Integer memoryReservationMib,
      ISecret registryCredential,
      Boolean bootstrap) {
    NetworkMode networkMode = bootstrap ? NetworkMode.AWS_VPC : NetworkMode.BRIDGE;
    TaskDefinition td = TaskDefinition.Builder.create(this, id)
        .compatibility(Compatibility.EC2)
        .family(family)
        .networkMode(networkMode)
        .build();
    ContainerImage image = ContainerImage.fromRegistry(imageName,
        RepositoryImageProps.builder()
            .credentials(registryCredential)
            .build());
    HealthCheck healthCheck = HealthCheck.builder().command(List.of(healthCheckCommand)).build();
    td.addContainer(id + "Container", ContainerDefinitionOptions.builder()
        .environment(environments)
        .image(image)
        .memoryReservationMiB(memoryReservationMib)
        .portMappings(portMappings)
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
    private final String portMappings;
    private final String serviceName;
    private final Boolean isPublic;
    private final String outputStack;
    private final String environment;
    private final String healthCheckCommand;
    private final Environment env;
    private final Boolean isBootstrap;
    private final String routeKey;
    private final String vpcName;
  }
}
