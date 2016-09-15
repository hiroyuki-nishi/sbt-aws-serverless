package com.github.yoshiyoshifujii.aws

import com.github.yoshiyoshifujii.aws.apigateway._
import com.github.yoshiyoshifujii.aws.lambda.AWSLambda
import sbt._

import scala.util.Try

object AWSServerlessPlugin extends AutoPlugin {

  object autoImport {
    lazy val deployLambda = taskKey[String]("")
    lazy val deploy = taskKey[File]("")
    lazy val deployDev = taskKey[File]("")
    lazy val deployResource = taskKey[Unit]("")
    lazy val listLambdaVersions = taskKey[Unit]("")
    lazy val listLambdaAliases = taskKey[Unit]("")

    lazy val awsLambdaFunctionName = settingKey[String]("")
    lazy val awsLambdaDescription = settingKey[String]("")
    lazy val awsLambdaHandler = settingKey[String]("")
    lazy val awsLambdaRole = settingKey[String]("")
    lazy val awsLambdaTimeout = settingKey[Int]("")
    lazy val awsLambdaMemorySize = settingKey[Int]("")
    lazy val awsLambdaS3Bucket = settingKey[String]("")
    lazy val awsLambdaDeployDescription = settingKey[String]("")
    lazy val awsLambdaAliasNames = settingKey[Seq[String]]("")

    lazy val awsApiGatewayResourcePath = settingKey[String]("")
    lazy val awsApiGatewayResourceHttpMethod = settingKey[String]("")
    lazy val awsApiGatewayResourceUriLambdaAlias = settingKey[String]("")
    lazy val awsApiGatewayIntegrationRequestTemplates = settingKey[Seq[(String, String)]]("")
    lazy val awsApiGatewayIntegrationResponseTemplates = settingKey[ResponseTemplates]("")
  }

  import autoImport._

  override def requires = sbtassembly.AssemblyPlugin

  override lazy val projectSettings = Seq(
    deployLambda := {
      val region = AWSApiGatewayPlugin.autoImport.awsRegion.value
      AWSLambda(region).deploy(
        functionName = awsLambdaFunctionName.value,
        role = awsLambdaRole.value,
        handler = awsLambdaHandler.value,
        bucketName = awsLambdaS3Bucket.value,
        jar = sbtassembly.AssemblyKeys.assembly.value,
        description = awsLambdaDescription.?.value,
        timeout = awsLambdaTimeout.?.value,
        memorySize = awsLambdaMemorySize.?.value
      ).get
    },
    deploy := {
      val region = AWSApiGatewayPlugin.autoImport.awsRegion.value
      val lambdaName = awsLambdaFunctionName.value
      val jar = sbtassembly.AssemblyKeys.assembly.value
      val lambda = AWSLambda(region)

      lazy val publish = lambda.publishVersion(
        functionName = awsLambdaFunctionName.value,
        description = awsLambdaDeployDescription.?.value
      )

      lazy val createAliases = (v: String) => Try {
        awsLambdaAliasNames.value map { name =>
          (for {
            a <- lambda.createAlias(
              functionName = lambdaName,
              name = s"$name$v",
              functionVersion = Some(v),
              description = None
            )
            p <- lambda.addPermission(
              functionName = a.getAliasArn
            )
          } yield a).get
        }
      }

      lazy val deployResource = (v: String) => AWSApiGatewayMethods(region).deploy(
        restApiId = AWSApiGatewayPlugin.autoImport.awsApiGatewayRestApiId.value,
        path = awsApiGatewayResourcePath.value,
        httpMethod = awsApiGatewayResourceHttpMethod.value,
        uri = Uri(
          region,
          AWSApiGatewayPlugin.autoImport.awsAccountId.value,
          lambdaName,
          awsApiGatewayResourceUriLambdaAlias.?.value.map(a => s"$a$v")
        ),
        requestTemplates = RequestTemplates(awsApiGatewayIntegrationRequestTemplates.value: _*),
        responseTemplates = awsApiGatewayIntegrationResponseTemplates.value
      )

      (for {
        lambdaArn <- Try(deployLambda.value)
        _ = {println(s"Lambda Deploy: $lambdaArn")}
        v <- publish
        _ = {println(s"Publish Lambda: ${v.getFunctionArn}")}
        cars <- createAliases(v.getVersion)
        _ = {cars.foreach(c => println(s"Create Alias: ${c.getAliasArn}"))}
        resource <- deployResource(v.getVersion)
        _ = {println(s"Api Gateway Deploy")}
      } yield jar).get
    },
    deployDev := {
      val region = AWSApiGatewayPlugin.autoImport.awsRegion.value
      val lambdaName = awsLambdaFunctionName.value
      val jar = sbtassembly.AssemblyKeys.assembly.value
      val lambda = AWSLambda(region)

      lazy val createAliasIfNotExists = Try {
        (for {
          aOp <- lambda.getAlias(lambdaName, "dev")
          res <- aOp map { a =>
            Try(a.getAliasArn)
          } getOrElse {
            for {
              a <- lambda.createAlias(
                functionName = lambdaName,
                name = s"dev",
                functionVersion = Some("$LATEST"),
                description = None
              )
              p <- lambda.addPermission(
                functionName = a.getAliasArn
              )
            } yield a.getAliasArn
          }
        } yield res).get
      }

      lazy val deployResource = AWSApiGatewayMethods(region).deploy(
        restApiId = AWSApiGatewayPlugin.autoImport.awsApiGatewayRestApiId.value,
        path = awsApiGatewayResourcePath.value,
        httpMethod = awsApiGatewayResourceHttpMethod.value,
        uri = Uri(
          region,
          AWSApiGatewayPlugin.autoImport.awsAccountId.value,
          lambdaName,
          awsApiGatewayResourceUriLambdaAlias.?.value
        ),
        requestTemplates = RequestTemplates(awsApiGatewayIntegrationRequestTemplates.value: _*),
        responseTemplates = awsApiGatewayIntegrationResponseTemplates.value
      )

      (for {
        lambdaArn <- Try(deployLambda.value)
        _ = {println(s"Lambda Deploy: $lambdaArn")}
        v <- createAliasIfNotExists
        _ = {println(s"Create Alias: $v")}
        resource <- deployResource
        _ = {println(s"Api Gateway Deploy")}
      } yield jar).get
    },
    deployResource := {
      val region = AWSApiGatewayPlugin.autoImport.awsRegion.value
      val lambdaName = awsLambdaFunctionName.value
      AWSApiGatewayMethods(region).deploy(
        restApiId = AWSApiGatewayPlugin.autoImport.awsApiGatewayRestApiId.value,
        path = awsApiGatewayResourcePath.value,
        httpMethod = awsApiGatewayResourceHttpMethod.value,
        uri = Uri(
          region,
          AWSApiGatewayPlugin.autoImport.awsAccountId.value,
          lambdaName,
          awsApiGatewayResourceUriLambdaAlias.?.value
        ),
        requestTemplates = RequestTemplates(awsApiGatewayIntegrationRequestTemplates.value: _*),
        responseTemplates = awsApiGatewayIntegrationResponseTemplates.value
      ).get
    },
    listLambdaVersions := {
      val region = AWSApiGatewayPlugin.autoImport.awsRegion.value
      AWSLambda(region)
        .printListVersionsByFunction(awsLambdaFunctionName.value)
        .get
    },
    listLambdaAliases := {
      val region = AWSApiGatewayPlugin.autoImport.awsRegion.value
      AWSLambda(region)
        .printListAliases(awsLambdaFunctionName.value)
        .get
    }
  )
}
