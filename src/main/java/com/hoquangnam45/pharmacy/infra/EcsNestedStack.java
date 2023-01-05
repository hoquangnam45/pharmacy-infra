package com.hoquangnam45.pharmacy.infra;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.IAutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.InstancesDistribution;
import software.amazon.awscdk.services.autoscaling.LaunchTemplateOverrides;
import software.amazon.awscdk.services.autoscaling.MixedInstancesPolicy;
import software.amazon.awscdk.services.autoscaling.Monitoring;
import software.amazon.awscdk.services.autoscaling.OnDemandAllocationStrategy;
import software.amazon.awscdk.services.autoscaling.SpotAllocationStrategy;
import software.amazon.awscdk.services.autoscaling.UpdatePolicy;
import software.amazon.awscdk.services.ec2.ILaunchTemplate;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.LaunchTemplate;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.UserData;
import software.amazon.awscdk.services.ecs.AsgCapacityProvider;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.EcsOptimizedImage;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.servicediscovery.IPrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;
import software.constructs.Construct;

@Getter
public class EcsNestedStack extends NestedStack {
  private static final String ASG = "Asg";

  private final ISecurityGroup sg;
  private final IAutoScalingGroup asg;
  private final AsgCapacityProvider asgCapacityProvider;
  private final ICluster cluster;
  private final IPrivateDnsNamespace namespace;

  public EcsNestedStack(final Construct scope, final String id, final EcsNestedStackProps props) {
    super(scope, id, props);

    IVpc vpc = props.getVpc();
    String launchTemplateUserData = props.getLaunchTemplateUserData();

    this.sg = createAsgSecurityGroup(ASG + "Sg", vpc);
    ILaunchTemplate lt = createLaunchTemplate(ASG + "Lt", launchTemplateUserData, vpc,
        sg, props.getKeypairName());
    this.asg = createAsg(ASG, lt, vpc, props.getSelectedSubnets());
    this.asgCapacityProvider = createAsgCapacityProvider(ASG + "CapacityProvider");
    this.cluster = createCluster("Cluster", props.getClusterName(), vpc, asgCapacityProvider);
    this.namespace = createNamespace("Namespace", props.getClusterName(), vpc);
  }

  private IPrivateDnsNamespace createNamespace(String id, String clusterName, IVpc vpc) {
    return PrivateDnsNamespace.Builder.create(this, id)
        .name(clusterName)
        .vpc(vpc)
        .build();
  }

  private Cluster createCluster(String id, String clusterName, IVpc vpc, AsgCapacityProvider capacityProvider) {
    Cluster cluster = Cluster.Builder.create(this, id)
        .vpc(vpc)
        .clusterName(clusterName)
        .build();
    cluster.addAsgCapacityProvider(asgCapacityProvider);
    return cluster;
  }

  // Asg capacity provider prevent deletion of cloudformation stack with enable
  // managed scaling protection
  // https://github.com/aws/aws-cdk/issues/14732
  private AsgCapacityProvider createAsgCapacityProvider(String id) {
    return AsgCapacityProvider.Builder.create(this, id)
        .autoScalingGroup(asg)
        .spotInstanceDraining(true)
        .enableManagedScaling(true)
        .enableManagedTerminationProtection(false)
        .build();
  }

  private ISecurityGroup createAsgSecurityGroup(String id, IVpc vpc) {
    return SecurityGroup.Builder.create(this, id)
        .allowAllOutbound(true)
        .vpc(vpc)
        .build();
  }

  private IAutoScalingGroup createAsg(String id, ILaunchTemplate launchTemplate, IVpc vpc,
      SelectedSubnets selectedSubnets) {
    InstancesDistribution instancesDistribution = InstancesDistribution.builder()
        .onDemandBaseCapacity(2)
        .onDemandPercentageAboveBaseCapacity(0)
        .spotAllocationStrategy(SpotAllocationStrategy.PRICE_CAPACITY_OPTIMIZED)
        .onDemandAllocationStrategy(OnDemandAllocationStrategy.PRIORITIZED)
        .build();
    List<LaunchTemplateOverrides> launchTemplateOverrides = List.of(
        LaunchTemplateOverrides.builder()
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
            .weightedCapacity(2)
            .build(),
        LaunchTemplateOverrides.builder()
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.NANO))
            .weightedCapacity(1)
            .build(),
        LaunchTemplateOverrides.builder()
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MEDIUM))
            .weightedCapacity(8)
            .build(),
        LaunchTemplateOverrides.builder()
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.LARGE))
            .weightedCapacity(16)
            .build(),
        LaunchTemplateOverrides.builder()
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.XLARGE))
            .weightedCapacity(32)
            .build(),
        LaunchTemplateOverrides.builder()
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.XLARGE2))
            .weightedCapacity(64)
            .build());
    MixedInstancesPolicy mixedInstancesPolicy = MixedInstancesPolicy.builder()
        .launchTemplate(launchTemplate)
        .instancesDistribution(instancesDistribution)
        .launchTemplateOverrides(launchTemplateOverrides)
        .build();
    return AutoScalingGroup.Builder.create(this, id)
        .maxCapacity(200)
        .minCapacity(2)
        .vpc(vpc)
        .updatePolicy(UpdatePolicy.rollingUpdate())
        .mixedInstancesPolicy(mixedInstancesPolicy)
        .instanceMonitoring(Monitoring.BASIC)
        .vpcSubnets(SubnetSelection.builder().subnets(selectedSubnets.getSubnets()).build())
        .build();
  }

  private ILaunchTemplate createLaunchTemplate(String id, String userDataContent, IVpc vpc,
      ISecurityGroup securityGroup, String keypairName) {
    List<IManagedPolicy> ec2ManagedPolicies = List.of(
        ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore"),
        ManagedPolicy.fromAwsManagedPolicyName("service-role/AmazonEC2ContainerServiceforEC2Role"));
    Role role = Role.Builder.create(this, id + "Role")
        .assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
        .managedPolicies(ec2ManagedPolicies)
        .build();
    UserData userData = UserData.forLinux();
    userData.addCommands(userDataContent);
    return LaunchTemplate.Builder.create(this, id)
        .machineImage(EcsOptimizedImage.amazonLinux2())
        .role(role)
        .securityGroup(securityGroup)
        .userData(userData)
        .keyName(keypairName)
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class EcsNestedStackProps implements NestedStackProps {
    private final String launchTemplateUserData;
    private final IVpc vpc;
    private final String keypairName;
    private final SelectedSubnets selectedSubnets;
    private final String clusterName;
  }
}
