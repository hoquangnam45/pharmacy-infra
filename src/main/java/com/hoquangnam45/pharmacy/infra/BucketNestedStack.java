package com.hoquangnam45.pharmacy.infra;

import lombok.Getter;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

@Getter
public class BucketNestedStack extends NestedStack {
  private final IBucket bucket;

  public BucketNestedStack(final Construct scope, final String id, final NestedStackProps props) {
    super(scope, id, props);

    this.bucket = createBucket("S3Bucket");
  }

  private IBucket createBucket(String id) {
    return Bucket.Builder.create(this, id)
        .versioned(true)
        .blockPublicAccess(BlockPublicAccess.BLOCK_ACLS)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build();
  }
}
