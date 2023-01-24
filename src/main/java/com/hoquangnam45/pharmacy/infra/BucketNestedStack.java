package com.hoquangnam45.pharmacy.infra;

import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

@Getter
public class BucketNestedStack extends NestedStack {
  private final IBucket bucket;

  public BucketNestedStack(final Construct scope, final String id, final BucketNestedStackProps props) {
    super(scope, id, props);

    this.bucket = createBucket("S3Bucket");
    List<String> resources = props.getPolicyStatement().getResources().stream()
        .map(bucket::arnForObjects)
        .collect(Collectors.toList());
    PolicyStatement policyStatement = props.getPolicyStatement().copy(PolicyStatementProps.builder()
        .resources(resources)
        .build());
    bucket.addToResourcePolicy(policyStatement);
  }

  private Bucket createBucket(String id) {
    return Bucket.Builder.create(this, id)
        .versioned(true)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class BucketNestedStackProps implements NestedStackProps {
    private final PolicyStatement policyStatement;
  }
}
