package com.hoquangnam45.pharmacy.deployment;

import java.util.List;

import com.hoquangnam45.pharmacy.constant.EnvironmentE;
import com.hoquangnam45.pharmacy.util.Environments;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.BucketDeploymentProps;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.constructs.Construct;

public class S3DeploymentStack extends Stack {
  public S3DeploymentStack(final Construct scope, final String id, final S3DeploymentStackProps props) {
    super(scope, id, props);
    String stackId = props.getOutputStack();
    EnvironmentE environment = EnvironmentE.fromValue(props.getEnvironment());

    String bucketName = null;
    switch (environment) {
      case DEV:
        bucketName = Fn.importValue(Environments.getOutputExportName(stackId, "S3BucketDevName"));
        break;
      case PROD:
        bucketName = Fn.importValue(Environments.getOutputExportName(stackId, "S3BucketProdName"));
        break;
      default:
        throw new UnsupportedOperationException();
    }
    IBucket destinationBucket = Bucket.fromBucketName(this, "Bucket", bucketName);
    new BucketDeployment(this, "Deployment", BucketDeploymentProps.builder()
        .destinationBucket(destinationBucket)
        .sources(List.of(Source.asset(props.getArtifactPath())))
        .build());
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class S3DeploymentStackProps implements StackProps {
    private final String artifactPath;
    private final String environment;
    private final Environment env;
    private final String outputStack;
  }
}
