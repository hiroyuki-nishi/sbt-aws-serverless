package com.github.yoshiyoshifujii.aws

import com.github.yoshiyoshifujii.aws.apigateway.{AWSApiGatewayAuthorize, Uri}
import com.github.yoshiyoshifujii.aws.lambda.AWSLambda
import sbt._

object AWSCustomAuthorizerPlugin extends AutoPlugin {

  object autoImport {
    lazy val getAuthorizers = taskKey[Unit]("")

    lazy val awsAuthorizerName = settingKey[String]("")
    lazy val awsIdentitySourceHeaderName = settingKey[String]("")
    lazy val awsIdentityValidationExpression = settingKey[String]("")
    lazy val awsAuthorizerResultTtlInSeconds = settingKey[Int]("")
  }

  import autoImport._
  import AWSServerlessPlugin.autoImport._
  import AWSApiGatewayPlugin.autoImport._

  override lazy val projectSettings = Seq(
    getAuthorizers := {
      val region = awsRegion.value
      AWSApiGatewayAuthorize(region).printAuthorizers(
        restApiId = awsApiGatewayRestApiId.value
      )
    },
    deploy := {
      val region = awsRegion.value
      val lambdaName = awsLambdaFunctionName.value
      val jar = sbtassembly.AssemblyKeys.assembly.value

      lazy val deployLambda = {
        AWSLambda(region).deploy(
          functionName = awsLambdaFunctionName.value,
          role = awsLambdaRole.value,
          handler = awsLambdaHandler.value,
          bucketName = awsLambdaS3Bucket.value,
          jar = sbtassembly.AssemblyKeys.assembly.value,
          description = awsLambdaDescription.?.value,
          timeout = awsLambdaTimeout.?.value,
          memorySize = awsLambdaMemorySize.?.value
        )
      }

      (for {
        lambdaArn <- deployLambda
        _ = {println(s"Lambda Deploy: $lambdaArn")}
        authorizerId <- AWSApiGatewayAuthorize(region).deployAuthorizer(
          restApiId = awsApiGatewayRestApiId.value,
          name = awsAuthorizerName.value,
          authorizerUri = Uri(
            region,
            awsAccountId.value,
            lambdaName,
            None
          ),
          identitySourceHeaderName = awsIdentitySourceHeaderName.value,
          identityValidationExpression = awsIdentityValidationExpression.?.value,
          authorizerResultTtlInSeconds = awsAuthorizerResultTtlInSeconds.?.value
        )
        _ = {println(s"API Gateway Authorizer Deploy: $authorizerId")}
      } yield jar).get
    },
    deployDev := deploy.value
  )
}
