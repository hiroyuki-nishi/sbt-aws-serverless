package com.github.yoshiyoshifujii.aws.cloudwatch

import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEventsClient
import com.amazonaws.services.cloudwatchevents.model.{ListRulesRequest, PutRuleRequest}
import com.github.yoshiyoshifujii.aws.AWSWrapper
import com.github.yoshiyoshifujii.cliformatter.CliFormatter

import scala.collection.JavaConverters._
import scala.util.Try

trait AWSCloudWatchWrapper extends AWSWrapper {

  val regionName: String

  lazy val client = {
    val c = new AmazonCloudWatchEventsClient()
    c.setRegion(RegionUtils.getRegion(regionName))
    c
  }

  def putRule(name: String, scheduleExpression: String) = Try {
    val request = new PutRuleRequest()
      .withName(name)
      .withScheduleExpression(scheduleExpression)
    client.putRule(request)
  }

  def listRules = Try {
    val request = new ListRulesRequest()

    client.listRules(request)
  }

  def printRules =
    for {
      rules <- listRules
    } yield {
      val p = CliFormatter(
        "Rules",
        "Name" -> 30,
        "ScheduleExpression" -> 30,
        "State" -> 10
      ).print3(
        rules.getRules.asScala.map { r =>
          (r.getName, r.getScheduleExpression, r.getState)
        }: _*)
      println(p)
    }
}

case class AWSCloudWatch(regionName: String) extends AWSCloudWatchWrapper
