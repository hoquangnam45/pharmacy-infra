package com.hoquangnam45.pharmacy.deployment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.IFileSystem;
import software.amazon.awscdk.services.efs.LifecyclePolicy;
import software.amazon.awscdk.services.efs.OutOfInfrequentAccessPolicy;
import software.constructs.Construct;

@Getter
public class FsNestedStack extends NestedStack {
  private final IFileSystem fileSystem;
  private final ISecurityGroup sg;

  public FsNestedStack(final Construct scope, final String id, final FsNestedStackProps props) {
    super(scope, id, props);
    this.sg = createSecurityGroup("Sg", props.getVpc());
    this.fileSystem = FileSystem.Builder.create(this, "Fs")
        .encrypted(true)
        .lifecyclePolicy(LifecyclePolicy.AFTER_1_DAY)
        .outOfInfrequentAccessPolicy(OutOfInfrequentAccessPolicy.AFTER_1_ACCESS)
        .vpc(props.getVpc())
        .removalPolicy(RemovalPolicy.DESTROY)
        .securityGroup(sg)
        .build();
  }

  private ISecurityGroup createSecurityGroup(String id, IVpc vpc) {
    return SecurityGroup.Builder.create(this, id)
        .allowAllOutbound(true)
        .vpc(vpc)
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class FsNestedStackProps implements NestedStackProps {
    private final IVpc vpc;
  }
}
