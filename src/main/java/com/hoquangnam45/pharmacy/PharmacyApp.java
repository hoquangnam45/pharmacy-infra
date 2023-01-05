package com.hoquangnam45.pharmacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.hoquangnam45.pharmacy.constant.DeploymentType;
import com.hoquangnam45.pharmacy.deployment.EcsServiceDeploymentStack;
import com.hoquangnam45.pharmacy.deployment.S3DeploymentStack;
import com.hoquangnam45.pharmacy.deployment.EcsServiceDeploymentStack.ApiGwConnectProps;
import com.hoquangnam45.pharmacy.deployment.EcsServiceDeploymentStack.EcsServiceDeploymentStackProps;
import com.hoquangnam45.pharmacy.deployment.S3DeploymentStack.S3DeploymentStackProps;
import com.hoquangnam45.pharmacy.infra.PharmacyStack;
import com.hoquangnam45.pharmacy.util.Numbers;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class PharmacyApp {
  private static final String INFRA_STACK_ID = "InfraStack";

  public static void main(final String[] args) throws JsonMappingException, JsonProcessingException {
    App app = new App();

    DeploymentType deploymentType = DeploymentType.valueOf(System.getenv("DEPLOYMENT_TYPE"));
    Environment environment = Environment.builder()
        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
        .region(System.getenv("CDK_DEFAULT_REGION"))
        .build();

    switch (deploymentType) {
      case ECS:
        boolean isPublic = Boolean.parseBoolean(System.getenv("IS_PUBLIC"));
        ApiGwConnectProps apiGwConnectProps = null;
        if (isPublic) {
          apiGwConnectProps = ApiGwConnectProps.builder()
              .routeKey(System.getenv("ROUTE_KEY"))
              .build();
        }
        new EcsServiceDeploymentStack(app, System.getenv("STACK_ID"), EcsServiceDeploymentStackProps.builder()
            .env(environment)
            .containerName(System.getenv("CONTAINER_NAME"))
            .containerPort(Integer.valueOf(System.getenv("CONTAINER_PORT")))
            .hostPort(Numbers.toInteger(System.getenv("HOST_PORT"), 0))
            .family(System.getenv("TASK_FAMILY"))
            .healthCheckCommand(System.getenv("HEALTH_CHECK_COMMAND"))
            .registryCredentialSecret(System.getenv("REGISTRY_CREDENTIAL"))
            .environment(System.getenv("ENVIRONMENT"))
            .exportedEnvironments(System.getenv("EXPORTED_ENVIRONMENTS"))
            .imageName(System.getenv("IMAGE_NAME"))
            .memoryReservationMiB(Numbers.toInteger(System.getenv("MEMORY_RESERVATION_MIB"), 128))
            .serviceName(System.getenv("SERVICE_NAME"))
            .isPublic(isPublic)
            .apiGwConnectProps(apiGwConnectProps)
            .outputStack(INFRA_STACK_ID)
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
        new PharmacyStack(app, INFRA_STACK_ID, StackProps.builder()
            .env(environment)
            .build());
    }

    app.synth();
  }

}
