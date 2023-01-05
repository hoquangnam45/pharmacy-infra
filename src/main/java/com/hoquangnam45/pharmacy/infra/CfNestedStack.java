package com.hoquangnam45.pharmacy.infra;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.NestedStack;
import software.amazon.awscdk.NestedStackProps;
import software.amazon.awscdk.services.apigatewayv2.alpha.IHttpApi;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.HttpOrigin;
import software.amazon.awscdk.services.cloudfront.origins.S3Origin;
import software.amazon.awscdk.services.s3.IBucket;
import software.constructs.Construct;

@Getter
public class CfNestedStack extends NestedStack {
  private final String distributionId;

  public CfNestedStack(Construct scope, String id, CfNestedStackProps props) {
    super(scope, id, props);
    Distribution distribution = createCloudFrontDistribution("Cf", props.getBucket(), props.getHttpApi());
    distributionId = distribution.getDistributionId();
  }

  private Distribution createCloudFrontDistribution(String id, IBucket bucket, IHttpApi httpApi) {
    BehaviorOptions s3Behavior = BehaviorOptions.builder()
        .allowedMethods(AllowedMethods.ALLOW_GET_HEAD_OPTIONS)
        .origin(S3Origin.Builder.create(bucket).build())
        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
        .build();
    BehaviorOptions apiBehavior = BehaviorOptions.builder()
        .allowedMethods(AllowedMethods.ALLOW_ALL)
        .origin(HttpOrigin.Builder.create(
            Fn.select(
                1,
                Fn.split("://", httpApi.getApiEndpoint())))
            .build())
        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
        .build();
    return Distribution.Builder.create(this, id)
        .defaultRootObject("index.html")
        .defaultBehavior(s3Behavior)
        .additionalBehaviors(Map.of(
            "/api/*", apiBehavior))
        .build();
  }

  @lombok.Builder
  @AllArgsConstructor
  @Getter
  public static final class CfNestedStackProps implements NestedStackProps {
    private final IBucket bucket;
    private final IHttpApi httpApi;
  }
}
