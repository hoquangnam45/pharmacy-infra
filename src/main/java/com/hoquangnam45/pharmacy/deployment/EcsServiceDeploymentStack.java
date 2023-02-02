package com.hoquangnam45.pharmacy.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.hoquangnam45.pharmacy.deployment.FsNestedStack.FsNestedStackProps;
import com.hoquangnam45.pharmacy.pojo.LoadBalancerPortMapping;
import com.hoquangnam45.pharmacy.util.Environments;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.ecs.AssociateCloudMapServiceOptions;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.CapacityProviderStrategy;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterAttributes;
import software.amazon.awscdk.services.ecs.Compatibility;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.Ec2Service;
import software.amazon.awscdk.services.ecs.EfsVolumeConfiguration;
import software.amazon.awscdk.services.ecs.HealthCheck;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.ecs.LoadBalancerTargetOptions;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.MountPoint;
import software.amazon.awscdk.services.ecs.NetworkMode;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.RepositoryImageProps;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancerAttributes;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.IApplicationTargetGroup;
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
    ISecurityGroup appSg = SecurityGroup.fromSecurityGroupId(this, "AppSg",
        Fn.importValue(Environments.getOutputExportName(stackId, "AppSg")));
    TaskDefinition td = createTaskDefinition("Td", props.getFamily(), props.getNetworkMode());

    ContainerDefinition containerDefintion = td.addContainer("ContainerDefintion", createContainerDefinition(
        props.getImageName(),
        props.getContainerName(),
        Environments.parseContainerPortMapping(props.getContainerPortMappings()),
        props.getContainerHealthCheckCmd(),
        Environments.collect(props.getExportedEnvironments()),
        props.getMemoryReservationMiB(),
        props.getMemoryLimitMib(),
        getSecret("Secret", props.getRegistryCredentialSecret())));

    if (props.getUseEfs()) {
      Map<Volume, MountPoint> mountPoints = provisionEfsVolume(props.getEfsVolumeMappings(), vpc, appSg);
      mountPoints.entrySet().stream().forEach(it -> {
        td.addVolume(it.getKey());
        containerDefintion.addMountPoints(it.getValue());
      });
    }

    Ec2Service ecsService = createService(
        "Service",
        cluster,
        td,
        containerDefintion.getContainerName(),
        props.getCloudMapPort(),
        Fn.importValue(Environments.getOutputExportName(stackId, "ClusterCapacityProviderName")),
        props.getServiceName(),
        namespace,
        props.getDaemon(),
        appSg,
        props.getNetworkMode());

    if (props.getUseLoadBalancer()) {
      IApplicationLoadBalancer alb = ApplicationLoadBalancer.fromApplicationLoadBalancerAttributes(this, "Lb",
          ApplicationLoadBalancerAttributes.builder()
              .loadBalancerArn(Fn.importValue(Environments.getOutputExportName(stackId, "LoadBalancerArn")))
              .securityGroupId(Fn.importValue(Environments.getOutputExportName(stackId, "LoadBalancerSg")))
              .build());
      List<LoadBalancerPortMapping> lbPortMappings = Environments.parseLbPortMapping(props.getLbPortMappings());
      integrateEcsContainerWithLbPortMappings("PortMapping", vpc, alb, ecsService, lbPortMappings,
          td.getDefaultContainer().getContainerName(), appSg);
    }
  }

  private Map<Volume, MountPoint> provisionEfsVolume(String mountPointMappings, IVpc vpc, ISecurityGroup appSg) {
    return Optional.ofNullable(mountPointMappings)
        .map(str -> str.trim())
        .filter(str -> !str.isEmpty())
        .map(str -> Arrays.asList(str.split(",")).stream())
        .map(mappings -> {
          return mappings
              .map(mapping -> mapping.trim())
              .map(mapping -> {
                List<String> tokens = Arrays.asList(mapping.split(":", 4));
                String volumeName = tokens.get(2).trim();
                FsNestedStack fsStack = new FsNestedStack(this, volumeName, FsNestedStackProps.builder()
                    .vpc(vpc)
                    .build());
                Environments.connect(appSg, fsStack.getSg());
                Volume volume = Volume.builder()
                    .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(fsStack.getFileSystem().getFileSystemId())
                        .build())
                    .name(volumeName)
                    .build();
                return Map.entry(volume, MountPoint.builder()
                    .containerPath(tokens.get(0).trim())
                    .readOnly(Boolean.parseBoolean(tokens.get(1).trim()))
                    .sourceVolume(volume.getName())
                    .build());
              })
              .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        })
        .orElse(Map.of());
  }

  private void integrateEcsContainerWithLbPortMappings(String id, IVpc vpc, IApplicationLoadBalancer alb,
      Ec2Service ecsService, List<LoadBalancerPortMapping> lbPortMappings, String containerName, ISecurityGroup appSg) {
    lbPortMappings.stream().forEach(lbPortMapping -> {
      Protocol protocol = Protocol.TCP;
      ApplicationProtocol applicationProtocol = ApplicationProtocol.HTTP;
      software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck healthCheck = software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck
          .builder()
          .path(lbPortMapping.getHealthCheckPath())
          .protocol(software.amazon.awscdk.services.elasticloadbalancingv2.Protocol.HTTP)
          .build();
      IApplicationTargetGroup targetGroup = ApplicationTargetGroup.Builder.create(this, id + "Tg")
          .healthCheck(healthCheck)
          .port(lbPortMapping.getTargetPort())
          .vpc(vpc)
          .protocol(applicationProtocol)
          .build();
      targetGroup.addTarget(ecsService.loadBalancerTarget(LoadBalancerTargetOptions.builder()
          .containerPort(lbPortMapping.getTargetPort())
          .protocol(protocol)
          .containerName(containerName)
          .build()));
      IApplicationListener listener = ApplicationListener.Builder.create(this, id + "Listener")
          .port(lbPortMapping.getListenerPort())
          .protocol(applicationProtocol)
          .loadBalancer(alb)
          .defaultTargetGroups(List.of(targetGroup))
          .open(lbPortMapping.getIsPublic())
          .build();
      listener.getConnections().addSecurityGroup(appSg);
      listener.getConnections().allowDefaultPortInternally();
    });
  }

  private Ec2Service createService(
      String id,
      ICluster cluster,
      TaskDefinition td,
      String containerName,
      Integer cloudMapPort,
      String capacityProviderName,
      String serviceName,
      IPrivateDnsNamespace namespace,
      Boolean daemon,
      ISecurityGroup appSg,
      NetworkMode networkMode) {
    List<CapacityProviderStrategy> capacityProviderStrategies = new ArrayList<>();
    if (!daemon) {
      capacityProviderStrategies.add(CapacityProviderStrategy.builder()
          .capacityProvider(capacityProviderName)
          .weight(100)
          .build());
    }
    Ec2Service ec2Service = Ec2Service.Builder.create(this, id)
        .capacityProviderStrategies(capacityProviderStrategies)
        .cluster(cluster)
        .serviceName(serviceName)
        .taskDefinition(td)
        .securityGroups(networkMode == NetworkMode.AWS_VPC ? List.of(appSg) : null)
        .daemon(daemon)
        .build();
    Service discoveryService = Service.Builder.create(this, "DiscoveryService")
        .namespace(namespace)
        .dnsRecordType(DnsRecordType.SRV)
        .name(serviceName)
        .build();
    ContainerDefinition containerDefinition = td.findContainer(containerName);
    ec2Service.associateCloudMapService(AssociateCloudMapServiceOptions.builder()
        .container(containerDefinition)
        .containerPort(cloudMapPort == null ? containerDefinition.getContainerPort()
            : cloudMapPort)
        .service(discoveryService)
        .build());
    return ec2Service;
  }

  private TaskDefinition createTaskDefinition(String id, String family, NetworkMode networkMode) {
    return TaskDefinition.Builder.create(this, id)
        .compatibility(Compatibility.EC2)
        .family(family)
        .networkMode(networkMode)
        .build();
  }

  private ContainerDefinitionOptions createContainerDefinition(
      String imageName,
      String containerName,
      List<PortMapping> containerPortMappings,
      String containerHealthCheckCmd,
      Map<String, String> environments,
      Integer memoryReservationMib,
      Integer memoryLimitMib,
      ISecret registryCredential) {
    ContainerImage image = ContainerImage.fromRegistry(imageName,
        RepositoryImageProps.builder()
            .credentials(registryCredential)
            .build());
    HealthCheck healthCheck = HealthCheck.builder().command(List.of(containerHealthCheckCmd)).build();
    return ContainerDefinitionOptions.builder()
        .environment(environments)
        .image(image)
        .memoryReservationMiB(memoryReservationMib)
        .memoryLimitMiB(memoryLimitMib)
        .portMappings(containerPortMappings)
        .containerName(containerName)
        .logging(LogDriver.awsLogs(
            AwsLogDriverProps.builder()
                .streamPrefix(getArtifactId() + "Log")
                .build()))
        .healthCheck(healthCheck)
        .build();
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
    private final Integer memoryLimitMib;
    private final String containerPortMappings;
    private final String serviceName;
    private final String outputStack;
    private final String environment;
    private final String containerHealthCheckCmd;
    private final Environment env;
    private final String routeKey;
    private final String vpcName;
    private final Integer cloudMapPort;
    private final Boolean useLoadBalancer;
    private final String lbPortMappings;
    private final NetworkMode networkMode;
    private final Boolean useEfs;
    private final String efsVolumeMappings;
    private final Boolean daemon;
  }
}
