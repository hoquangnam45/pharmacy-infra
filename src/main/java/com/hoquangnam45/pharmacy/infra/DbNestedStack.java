package com.hoquangnam45.pharmacy.infra;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.ec2.ISecurityGroup;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SelectedSubnets;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.IInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
import software.constructs.Construct;

@Getter
public class DbNestedStack extends NestedStack {
  private final ISecurityGroup sg;

  public DbNestedStack(Construct scope, String id, DbNestedStackProps props) {
    super(scope, id, props);

    this.sg = createDbSecurityGroup("Sg", props.getVpc());
    createPostgresDb("Instance", props.getVpc(), sg, props.getSelectedSubnets(), props.getCredentials());
  }

  private ISecurityGroup createDbSecurityGroup(String id, IVpc vpc) {
    return SecurityGroup.Builder.create(this, id)
        .allowAllOutbound(true)
        .vpc(vpc)
        .build();
  }

  private void createPostgresDb(String id, IVpc vpc, ISecurityGroup dbSg, SelectedSubnets selectedSubnets,
      Credentials credentials) {
    IInstanceEngine engine = DatabaseInstanceEngine
        .postgres(PostgresInstanceEngineProps.builder()
            .version(PostgresEngineVersion.VER_14_2)
            .build());
    DatabaseInstance.Builder.create(this, id)
        .engine(engine)
        .allocatedStorage(20)
        .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
        .vpc(vpc)
        .credentials(credentials)
        .backupRetention(Duration.days(0))
        .securityGroups(List.of(dbSg))
        .vpcSubnets(SubnetSelection.builder().subnets(selectedSubnets.getSubnets()).build())
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class DbNestedStackProps implements NestedStackProps {
    private final IVpc vpc;
    private final SelectedSubnets selectedSubnets;
    private final Credentials credentials;
  }
}