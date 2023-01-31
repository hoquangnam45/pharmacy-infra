package com.hoquangnam45.pharmacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.hoquangnam45.pharmacy.constant.DeploymentType;
import com.hoquangnam45.pharmacy.deployment.EcsServiceDeploymentStack;
import com.hoquangnam45.pharmacy.deployment.S3DeploymentStack;
import com.hoquangnam45.pharmacy.deployment.EcsServiceDeploymentStack.EcsServiceDeploymentStackProps;
import com.hoquangnam45.pharmacy.deployment.S3DeploymentStack.S3DeploymentStackProps;
import com.hoquangnam45.pharmacy.infra.PharmacyStack;
import com.hoquangnam45.pharmacy.infra.PharmacyStack.PharmacyStackProps;
import com.hoquangnam45.pharmacy.util.Numbers;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;

public class PharmacyApp {
  private final static String INFRA_STACK_ID = "InfraStack";
  private final static String VPC_NAME = "pharmacy-vpc";

  public static void main(final String[] args) throws JsonMappingException, JsonProcessingException {
    App app = new App();

    DeploymentType deploymentType = DeploymentType.valueOf(System.getenv("DEPLOYMENT_TYPE"));
    Environment environment = Environment.builder()
        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
        .region(System.getenv("CDK_DEFAULT_REGION"))
        .build();

    switch (deploymentType) {
      case ECS:
        new EcsServiceDeploymentStack(app, System.getenv("STACK_ID"), EcsServiceDeploymentStackProps.builder()
            .env(environment)
            .containerName(System.getenv("CONTAINER_NAME"))
            .portMappings(System.getenv("PORT_MAPPINGS"))
            .family(System.getenv("TASK_FAMILY"))
            .routeKey(System.getenv("ROUTE_KEY"))
            .healthCheckCommand(System.getenv("HEALTH_CHECK_COMMAND"))
            .registryCredentialSecret(System.getenv("REGISTRY_CREDENTIAL"))
            .environment(System.getenv("ENVIRONMENT"))
            .exportedEnvironments(System.getenv("EXPORTED_ENVIRONMENTS"))
            .imageName(System.getenv("IMAGE_NAME"))
            .memoryReservationMiB(Numbers.toInteger(System.getenv("MEMORY_RESERVATION_MIB"), 128))
            .serviceName(System.getenv("SERVICE_NAME"))
            .isPublic(Boolean.parseBoolean(System.getenv("IS_PUBLIC")))
            .isBootstrap(Boolean.parseBoolean(System.getenv("IS_BOOTSTRAP")))
            .outputStack(INFRA_STACK_ID)
            .vpcName(VPC_NAME)
            .build());
        break;
      case S3:
        new S3DeploymentStack(app, System.getenv("STACK_ID"), S3DeploymentStackProps.builder()
            .env(environment)
            .artifactPath(System.getenv("ARTIFACT_PATH"))
            .environment(System.getenv("ENVIRONMENT"))
            .outputStack(INFRA_STACK_ID)
            .build());
        break;
      case INFRA:
      default:
        new PharmacyStack(app, INFRA_STACK_ID, PharmacyStackProps.builder()
            .env(environment)
            .vpcName(VPC_NAME)
            .build());
    }

    app.synth();
  }
}
