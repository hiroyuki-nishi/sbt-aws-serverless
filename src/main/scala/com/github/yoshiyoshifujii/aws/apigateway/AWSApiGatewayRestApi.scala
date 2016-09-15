package com.github.yoshiyoshifujii.aws.apigateway

import java.io.File

import com.amazonaws.services.apigateway.model._
import com.github.yoshiyoshifujii.cliformatter.CliFormatter

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.util.Try

trait AWSApiGatewayRestApiWrapper extends AWSApiGatewayWrapper {

  def create(name: String,
             description: Option[String]) = Try {
    val request = new CreateRestApiRequest()
      .withName(name)
    description.foreach(request.setDescription)

    client.createRestApi(request)
  }

  def delete(restApiId: RestApiId) = Try {
    val request = new DeleteRestApiRequest()
      .withRestApiId(restApiId)

    client.deleteRestApi(request)
  }

  def get(restApiId: RestApiId) = Try {
    val request = new GetRestApiRequest()
      .withRestApiId(restApiId)

    toOpt(client.getRestApi(request))
  }

  def gets = Try {
    val request = new GetRestApisRequest()

    client.getRestApis(request)
  }

  def printGets = {
    for {
      l <- gets
    } yield {
      val p = CliFormatter(
        "Rest APIs",
        "Created Date" -> 30,
        "Rest API Id" -> 15,
        "Rest API Name" -> 20,
        "Description" -> 30
      ).print4(
        l.getItems map { d =>
          (d.getCreatedDate.toString, d.getId, d.getName, d.getDescription)
        }: _*)
      println(p)
    }
  }

  def `import`(body: File,
               failOnWarnings: Option[Boolean]) = Try {
    val request = new ImportRestApiRequest()
      .withBody(toByteBuffer(body))
    failOnWarnings.foreach(request.setFailOnWarnings(_))

    client.importRestApi(request)
  }

  def put(restApiId: RestApiId,
          body: File,
          mode: PutMode,
          failOnWarnings: Option[Boolean]) = Try {
    val request = new PutRestApiRequest()
      .withRestApiId(restApiId)
      .withBody(toByteBuffer(body))
      .withMode(mode)
    failOnWarnings.foreach(request.setFailOnWarnings(_))

    client.putRestApi(request)
  }

  def createDeployment(restApiId: RestApiId,
                       stageName: StageName,
                       stageDescription: Option[StageDescription],
                       description: Option[String],
                       variables: Option[StageVariables]) = Try {
    val request = new CreateDeploymentRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
    stageDescription.foreach(request.setStageDescription)
    description.foreach(request.setDescription)
    variables.foreach(v => request.setVariables(v.asJava))

    client.createDeployment(request)
  }

  def createStage(restApiId: RestApiId,
                  stageName: StageName,
                  deploymentId: DeploymentId,
                  description: Option[StageDescription],
                  variables: Option[StageVariables]) = Try {
    val request = new CreateStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
      .withDeploymentId(deploymentId)
    description.foreach(request.setDescription)
    variables.foreach(v => request.setVariables(v.asJava))

    client.createStage(request)
  }

  def getStage(restApiId: RestApiId,
               stageName: StageName) = Try {
    val request = new GetStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)

    toOpt(client.getStage(request))
  }

  def updateStage(restApiId: RestApiId,
                  stageName: StageName,
                  deploymentId: DeploymentId) = Try {
    val po = new PatchOperation()
      .withOp(Op.Replace)
      .withPath("/deploymentId")
      .withValue(deploymentId)

    val request = new UpdateStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
      .withPatchOperations(po)

    client.updateStage(request)
  }

  def createOrUpdateStage(restApiId: RestApiId,
                          stageName: StageName,
                          deploymentId: DeploymentId,
                          description: Option[StageDescription],
                          variables: Option[StageVariables]) = {
    for {
      sOp <- getStage(restApiId, stageName)
      res <- Try {
        sOp map { s =>
          updateStage(
            restApiId = restApiId,
            stageName = stageName,
            deploymentId = deploymentId
          ).get.getDeploymentId
        } getOrElse {
          createStage(
            restApiId = restApiId,
            stageName = stageName,
            deploymentId = deploymentId,
            description = description,
            variables = variables).get.getDeploymentId
        }
      }
    } yield res
  }

  def getDeployments(restApiId: RestApiId) = Try {
    val request = new GetDeploymentsRequest()
      .withRestApiId(restApiId)

    client.getDeployments(request)
  }

  def printDeployments(restApiId: RestApiId) = {
    for {
      l <- getDeployments(restApiId)
    } yield {
      val p = CliFormatter(
        restApiId,
        "Created Date" -> 30,
        "Deployment Id" -> 15,
        "Description" -> 30
      ).print3(
        l.getItems map { d =>
          (d.getCreatedDate.toString, d.getId, d.getDescription)
        }: _*)
      println(p)
    }
  }

  def getStages(restApiId: RestApiId) = Try {
    val request = new GetStagesRequest()
      .withRestApiId(restApiId)

    client.getStages(request)
  }

  def printStages(restApiId: RestApiId) = {
    for {
      l <- getStages(restApiId)
    } yield {
      val p = CliFormatter(
        restApiId,
        "Stage Name" -> 10,
        "Last Updated Date" -> 30,
        "Deployment Id" -> 15,
        "Description" -> 30
      ).print4(
        l.getItem map { s =>
          (s.getStageName, s.getLastUpdatedDate.toString, s.getDeploymentId, s.getDescription)
        }: _*)
      println(p)
    }
  }

  def getResources(restApiId: RestApiId) = Try {
    val request = new GetResourcesRequest()
      .withRestApiId(restApiId)

    client.getResources(request)
  }

  def printResources(restApiId: RestApiId) =
    for {
      l <- getResources(restApiId)
    } yield {
      val p = CliFormatter(
        restApiId,
        "Resource Id" -> 15,
        "Resource Path" -> 30,
        "Method Keys" -> 30
      ).print3(
        l.getItems map { r =>
          (r.getId, r.getPath, ("" /: r.getResourceMethods.keys)(_ + "," + _))
        }: _*)
      println(p)

    }

}
case class AWSApiGatewayRestApi(regionName: String) extends AWSApiGatewayRestApiWrapper

