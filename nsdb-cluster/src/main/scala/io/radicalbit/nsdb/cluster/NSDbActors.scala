/*
 * Copyright 2018-2020 Radicalbit S.r.l.
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

package io.radicalbit.nsdb.cluster

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata.DistributedData
import akka.cluster.singleton._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.remote.RemoteScope
import akka.util.Timeout
import io.radicalbit.nsdb.cluster.actor._

import scala.concurrent.ExecutionContextExecutor

/**
  * Creates the top level actor [[DatabaseActorsGuardian]] and grpc endpoint [[GrpcEndpoint]] based on coordinators
  */
trait NSDbActors {

  implicit def system: ActorSystem

  implicit lazy val timeout: Timeout =
    Timeout(system.settings.config.getDuration("nsdb.global.timeout", TimeUnit.SECONDS), TimeUnit.SECONDS)

  implicit lazy val executionContext: ExecutionContextExecutor = system.dispatcher

  def initTopLevelActors(): Unit = {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()

    system.actorOf(
      ClusterSingletonManager.props(singletonProps = Props(classOf[DatabaseActorsGuardian]),
                                    terminationMessage = PoisonPill,
                                    settings = ClusterSingletonManagerSettings(system)),
      name = "databaseActorGuardian"
    )

    DistributedData(system).replicator

    system.actorOf(
      ClusterSingletonProxy.props(singletonManagerPath = "/user/databaseActorGuardian",
                                  settings = ClusterSingletonProxySettings(system)),
      name = "databaseActorGuardianProxy"
    )

    system
      .actorOf(
        Props[ClusterListener].withDeploy(Deploy(scope = RemoteScope(Cluster(system).selfMember.address))),
        name = s"cluster-listener_${createNodeName(Cluster(system).selfMember)}"
      )
  }
}
