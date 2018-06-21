/*
 * Copyright 2018 Radicalbit S.r.l.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.radicalbit.nsdb.cluster.coordinator

import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.radicalbit.nsdb.cluster.NsdbPerfLogger
import io.radicalbit.nsdb.cluster.coordinator.ReadCoordinator.Commands.GetConnectedNodes
import io.radicalbit.nsdb.cluster.coordinator.ReadCoordinator.Events.ConnectedNodesGot
import io.radicalbit.nsdb.cluster.coordinator.ReadCoordinator.PendingRequestStatus
import io.radicalbit.nsdb.common.protocol.Bit
import io.radicalbit.nsdb.common.statement.SelectSQLStatement
import io.radicalbit.nsdb.protocol.MessageProtocol.Commands._
import io.radicalbit.nsdb.protocol.MessageProtocol.Events._
import io.radicalbit.nsdb.util.PipeableFutureWithSideEffect._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
  * Actor that receives and handles every read request.
  * @param metadataCoordinator  [[MetadataCoordinator]] the metadata coordinator.
  * @param metricsSchemaActor [[io.radicalbit.nsdb.cluster.actor.MetricsSchemaActor]] the metrics schema actor.
  */
class ReadCoordinator(metadataCoordinator: ActorRef, metricsSchemaActor: ActorRef)
    extends Actor
    with ActorLogging
    with NsdbPerfLogger {

  implicit val timeout: Timeout = Timeout(
    context.system.settings.config.getDuration("nsdb.read-coordinatoor.timeout", TimeUnit.SECONDS),
    TimeUnit.SECONDS)

  lazy val sharding: Boolean          = context.system.settings.config.getBoolean("nsdb.sharding.enabled")
  lazy val shardingInterval: Duration = context.system.settings.config.getDuration("nsdb.sharding.interval")

  import context.dispatcher

  private val metricsDataActors: mutable.Map[String, ActorRef] = mutable.Map.empty

  private val processingRequests: mutable.Map[String, PendingRequestStatus] = mutable.Map.empty

  override def receive: Receive = {
    case SubscribeMetricsDataActor(actor: ActorRef, nodeName) =>
      metricsDataActors += (nodeName -> actor)
      sender() ! MetricsDataActorSubscribed(actor, nodeName)
    case GetConnectedNodes =>
      sender ! ConnectedNodesGot(metricsDataActors.keys.toSeq)
    case msg @ GetDbs =>
      Future
        .sequence(metricsDataActors.values.toSeq.map(actor => (actor ? msg).mapTo[DbsGot].map(_.dbs)))
        .map(_.flatten.toSet)
        .map(dbs => DbsGot(dbs))
        .pipeTo(sender)
    case msg @ GetNamespaces(db) =>
      Future
        .sequence(metricsDataActors.values.toSeq.map(actor => (actor ? msg).mapTo[NamespacesGot].map(_.namespaces)))
        .map(_.flatten.toSet)
        .map(namespaces => NamespacesGot(db, namespaces))
        .pipeTo(sender)
    case msg @ GetMetrics(db, namespace) =>
      Future
        .sequence(metricsDataActors.values.toSeq.map(actor => (actor ? msg).mapTo[MetricsGot].map(_.metrics)))
        .map(_.flatten.toSet)
        .map(metrics => MetricsGot(db, namespace, metrics))
        .pipeTo(sender)
    case msg: GetSchema =>
      metricsSchemaActor forward msg

    case SelectStatementFailed(reason, errorCode, purpose, requestId, replyTo) =>
      processingRequests.get(requestId).foreach { _ =>
        replyTo ! SelectStatementFailed(reason, errorCode, purpose, requestId)
        processingRequests -= requestId
      }

    case msg @ SelectStatementExecuted(_, _, _, values, _, requestId, replyTo) =>
      processingRequests.get(requestId).foreach { requestStatus =>
        requestStatus.partials += values
        if (requestStatus.partials.size == requestStatus.noPartialNeeded) {
          replyTo ! msg
          processingRequests -= requestId
        }
      }

    case SchemaGot(_, _, _, Some(schema), purpose, requestId, replyTo) =>
      processingRequests.get(requestId).foreach { reqStatus =>
        metricsDataActors.values.toSeq.foreach(actor =>
          actor ! ExecuteSelectStatement(reqStatus.statement, schema, purpose, requestId, replyTo))
      }

    case SchemaGot(_, _, metric, None, _, requestId, replyTo) =>
      processingRequests -= requestId
      replyTo ! SelectStatementFailed(s"Metric $metric does not exist ", MetricNotFound(metric))

    case ExecuteStatement(statement, purpose) =>
      val requestId = UUID.randomUUID().toString
      log.debug("executing request {} with id {} at {}", statement, requestId, System.currentTimeMillis())
      processingRequests += (requestId -> PendingRequestStatus(statement = statement,
                                                               noPartialNeeded = metricsDataActors.size))
      metricsSchemaActor ! GetSchema(statement.db, statement.namespace, statement.metric, purpose, requestId, sender)
  }
}

object ReadCoordinator {

  private case class PendingRequestStatus(statement: SelectSQLStatement,
                                          startTimestamp: Long = System.currentTimeMillis(),
                                          noPartialNeeded: Int,
                                          partials: ListBuffer[Seq[Bit]] = ListBuffer.empty)

  object Commands {
    private[coordinator] case object GetConnectedNodes
  }

  object Events {
    private[coordinator] case class ConnectedNodesGot(nodes: Seq[String])
  }

  def props(metadataCoordinator: ActorRef, schemaActor: ActorRef): Props =
    Props(new ReadCoordinator(metadataCoordinator, schemaActor))

}
