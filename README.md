# sbt-aws-serverless

sbt plugin to deploy code to Amazon API Gateway and AWS Lambda

## Installation

Add the following to your `project/plugins.sbt` file:

```sbt
addSbtPlugin("com.github.yoshiyoshifujii" % "sbt-aws-serverless" % "4.1.0")
```

Add the `ServerlessPlugin` auto-plugin to your build.sbt:

```sbt
enablePlugins(ServerlessPlugin)
```

## Usage

`sbt serverlessDeploy <stage>` deploys the entire service.

`sbt serverlessDeployDev <stage>` Deploy the deployDev task in development mode.

`sbt serverlessDeployFunction <functionName> <stage>` The deployFunc task deploys the AWS Lambda Function.

`sbt serverlessDeployList <stage>` The deployList task will list your recent deployments.

`sbt serverlessInvoke <stage>` Invokes deployed function.

`sbt serverlessInformation` Displays information about the deployed service.

`sbt serverlessRemove` The remove task will remove the deployed service.

`sbt serverlessRemoveStage <stage>` The removeStage task will remove the stage.

`sbt serverlessRemoveDeployment <deploymentId>` remove the API Gateway deployments.

`sbt serverlessDeployStream <stage>` The deployStream task deploys the AWS Stream Event.

`sbt serverlessClean` Clean up unneccessary deployments.

`sbt serverlessFunctionsDeploy <stage>` The functionDeploy task deploys the AWS Lambda Function and API Gateway.

## Configuration

```sbt
serverlessOption := {
  ServerlessOption(
    Provider(
      awsAccount: String,
      region: String = "us-east-1",
      deploymentBucket: String
    ),
    ApiGateway(
      swagger: File,
      stageVariables: Option[Map[String, String]] = Some(Map(
        "env" -> stage,
        "region" -> region
      ))
    ),
    Functions(
      Function(
        filePath: File,
        name: String,
        description: Option[String] = None,
        handler: String,
        memorySize: Int = 512,
        timeout: Int = 10,
        role: String,
        environment: Map[String, String] = Map.empty,
        tracing: Option[Tracing] = None,
        events: Events = Events(
          HttpEvent(
            path: String,
            method: String,
            uriLambdaAlias: String = "${stageVariables.env}",
            proxyIntegration: Boolean = false,
            cors: Boolean = false,
            `private`: Boolean = false,
            authorizerName: Option[String] = None,
            request: Request = Request(),
            invokeInput: Option[HttpInvokeInput] = None
          ),
          AuthorizeEvent(
            name: String,
            uriLambdaAlias: String = "${stageVariables.env}",
            resultTtlInSeconds: Int = 1800,
            identitySourceHeaderName: String = "Authorization",
            identityValidationExpression: Option[String] = None
          ),
          KinesisStreamEvent(
            name: String,
            batchSize: Int = 100,
            startingPosition: KinesisStartingPosition = KinesisStartingPosition.TRIM_HORIZON,
            enabled: Boolean = true,
            oldFunctions: Seq[FunctionBase] = Seq.empty
          ),
          DynamoDBStreamEvent(
            name: String,
            batchSize: Int = 100,
            startingPosition: DynamoDBStartingPosition = DynamoDBStartingPosition.TRIM_HORIZON,
            enabled: Boolean = true,
            oldFunctions: Seq[FunctionBase] = Seq.empty
          )
        )
      ),
      NotDeployLambdaFunction(
        name: String,
        publishedVersion: Option[String] = None,
        events: Events
      )
    )
  )
}
```

If there is a Jar of the same name in S3, we added an option to preferentially use that.

```sbt
serverlessNoUploadMode := true
```

## build.sbt

An example configuration might look like this:

```sbt
import Dependencies._
import serverless._

lazy val accountId = sys.props.getOrElse("AWS_ACCOUNT_ID", "")
lazy val roleArn = sys.props.getOrElse("AWS_ROLE_ARN", "")
lazy val bucketName = sys.props.getOrElse("AWS_BUCKET_NAME", "")
lazy val authKey = sys.props.getOrElse("AUTH_KEY", "")

val commonSettings = Seq(
  version := "$version$",
  scalaVersion := "2.12.1",
  organization := "$organization$",
  libraryDependencies ++= rootDeps
)

val assemblySettings = Seq(
  assemblyMergeStrategy in assembly := {
    case "application.conf" => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  assemblyJarName in assembly := s"\${name.value}-\${version.value}.jar",
  publishArtifact in (Compile, packageBin) := false,
  publishArtifact in (Compile, packageSrc) := false,
  publishArtifact in (Compile, packageDoc) := false
)

lazy val root = (project in file(".")).
  enablePlugins(ServerlessPlugin).
  aggregate(auth, appHello, appAccountModified).
  settings(commonSettings: _*).
  settings(
    name := "$name$",
    serverlessOption := {
      ServerlessOption(
        Provider(
          awsAccount = accountId,
          deploymentBucket = bucketName,
          swagger = file("./swagger.yaml")
        ),
        Functions(
          Function(
            filePath = (assembly in auth).value,
            name = (name in auth).value,
            handler = "$package$.Auth::handleRequest",
            role = roleArn,
            events = Events(
              AuthorizeEvent(
                name = (name in auth).value
              )
            )
          ),
          Function(
            filePath = (assembly in appHello).value,
            name = (name in appHello).value,
            handler = "$package$.application.hello.App::handleRequest",
            role = roleArn,
            events = Events(
              HttpEvent(
                path = "/hellos",
                method = "GET",
                proxyIntegration = false,
                cors = true,
                authorizerName = (name in auth).value,
                invokeInput = HttpInvokeInput(
                  headers = Seq("Authorization" -> authKey)
                )
              )
            )
          ),
          Function(
            filePath = (assembly in appAccountModified).value,
            name = (name in appAccountModified).value,
            handler = "$package$.application.accountmodified.App::recordHandler",
            role = roleArn
          )
        )
      )
    }
  )

lazy val domain = (project in file("./modules/domain")).
  settings(commonSettings: _*).
  settings(
    name := "$name$-domain",
    libraryDependencies ++= domainDeps
  )

lazy val infraLambda = (project in file("./modules/infrastructure/lambda")).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-lambda",
    libraryDependencies ++= infraLambdaDeps
  )

lazy val infraLambdaConsumer = (project in file("./modules/infrastructure/lambdaconsumer")).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-lambda-consumer",
    libraryDependencies ++= infraLambdaConsumerDeps
  )

lazy val infraDomain = (project in file("./modules/infrastructure/domain")).
  dependsOn(domain).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-domain",
    libraryDependencies ++= infraDomainDeps
  )

lazy val infraDynamo = (project in file("./modules/infrastructure/dynamodb")).
  dependsOn(infraDomain).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-dynamodb",
    libraryDependencies ++= infraDynamoDeps
  )

lazy val infraS3 = (project in file("./modules/infrastructure/s3")).
  dependsOn(infraDomain).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-s3",
    libraryDependencies ++= infraS3Deps
  )

lazy val infraKinesis = (project in file("./modules/infrastructure/kinesis")).
  dependsOn(infraDomain).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-kinesis",
    libraryDependencies ++= infraKinesisDeps
  )

lazy val infraElasticSearch = (project in file("./modules/infrastructure/elasticsearch")).
  dependsOn(infraDomain).
  settings(commonSettings: _*).
  settings(
    name := "$name$-infrastructure-elasticsearch",
    libraryDependencies ++= infraElasticSearchDeps
  )

lazy val auth = (project in file("./modules/auth")).
  settings(commonSettings: _*).
  settings(assemblySettings: _*).
  settings(
    name := "$name$-auth",
    libraryDependencies ++= authDeps
  )

lazy val appHello = (project in file("./modules/application/hello")).
  dependsOn(infraLambda, infraDynamo, infraS3, infraKinesis).
  settings(commonSettings: _*).
  settings(assemblySettings: _*).
  settings(
    name := "$name$-app-hello",
    libraryDependencies ++= appHelloDeps
  )

lazy val appAccountModified = (project in file("./modules/application/accountmodified")).
  dependsOn(infraLambdaConsumer, infraDomain).
  settings(commonSettings: _*).
  settings(assemblySettings: _*).
  settings(
    name := "$name$-app-account-modified",
    libraryDependencies ++= appAccountModifiedDeps
  )
```

An example command might look like this:

```sh
sbt -DAWS_ACCOUNT_ID=<AWS Account ID> \
    -DAWS_ROLE_ARN=arn:aws:iam::<AWS Account ID>:role/<Role Name> \
    -DAWS_BUCKET_NAME=<Bucket Name> \
    -DAUTH_KEY=hoge
```

## Giter8

https://github.com/yoshiyoshifujii/sbt-aws-serverless-ddd.g8

