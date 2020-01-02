package io.radicalbit.nsdb.cluster.actor

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.{Cluster, MemberStatus}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.radicalbit.nsdb.cluster.coordinator.MetadataCoordinator.commands._
import io.radicalbit.nsdb.cluster.coordinator.MetadataCoordinator.events._
import io.radicalbit.nsdb.common.model.MetricInfo
import io.radicalbit.nsdb.model.Location
import io.radicalbit.rtsae.STMultiNodeSpec

import scala.concurrent.Await
import scala.concurrent.duration._

object MetadataSpec extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")

  commonConfig(ConfigFactory.parseString("""
    |akka.loglevel = ERROR
    |akka.actor{
    | provider = "cluster"
    | control-aware-dispatcher {
    |     mailbox-type = "akka.dispatch.UnboundedControlAwareMailbox"
    |   }
    |}
    |akka.log-dead-letters-during-shutdown = off
    |nsdb{
    |
    |  grpc {
    |    interface = "0.0.0.0"
    |    port = 7817
    |  }
    |
    |  read-coordinator.timeout = 10 seconds
    |  namespace-schema.timeout = 10 seconds
    |  namespace-data.timeout = 10 seconds
    |  rpc-endpoint.timeout = 30 seconds
    |  publisher.timeout = 10 seconds
    |  publisher.scheduler.interval = 5 seconds
    |  write.scheduler.interval = 15 seconds
    |  retention.check.interval = 1 seconds
    |
    |  sharding {
    |    interval = 1d
    |    passivate-after = 1h
    |  }
    |
    |  read {
    |    parallelism {
    |      initial-size = 1
    |      lower-bound= 1
    |      upper-bound = 1
    |    }
    |  }
    |
    |  storage {
    |    base-path  = "target/test_index/MetadataTest"
    |    index-path = ${nsdb.storage.base-path}"/index"
    |    commit-log-path = ${nsdb.storage.base-path}"/commit_log"
    |    metadata-path = ${nsdb.storage.base-path}"/metadata"
    |  }
    |
    |  write-coordinator.timeout = 5 seconds
    |  metadata-coordinator.timeout = 5 seconds
    |  commit-log {
    |    serializer = "io.radicalbit.nsdb.commit_log.StandardCommitLogSerializer"
    |    writer = "io.radicalbit.nsdb.commit_log.RollingCommitLogFileWriter"
    |    directory = "target/commitLog"
    |    max-size = 50000
    |    passivate-after = 5s
    |  }
    |  websocket {
    |    refresh-period = 100
    |    retention-size = 10
    |  }
    |}
    """.stripMargin))

  nodeConfig(node1)(ConfigFactory.parseString("""
      |akka.remote.netty.tcp.port = 25520
    """.stripMargin))

  nodeConfig(node2)(ConfigFactory.parseString("""
      |akka.remote.netty.tcp.port = 25530
    """.stripMargin))

}

class MetadataSpecMultiJvmNode1 extends MetadataSpec {}

class MetadataSpecMultiJvmNode2 extends MetadataSpec {}

class MetadataSpec extends MultiNodeSpec(MetadataSpec) with STMultiNodeSpec with ImplicitSender {

  import MetadataSpec._

  override def initialParticipants = roles.size

  val cluster = Cluster(system)

  val mediator = DistributedPubSub(system).mediator

  val guardian = system.actorOf(Props[DatabaseActorsGuardian], "guardian")

  implicit val timeout: Timeout = Timeout(5.seconds)

  system.actorOf(Props[ClusterListenerTestActor], name = "clusterListener")

  lazy val nodeName = s"${cluster.selfAddress.host.getOrElse("noHost")}_${cluster.selfAddress.port.getOrElse(2552)}"

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {

      cluster join node(to).address
    }
    enterBarrier(from.name + "-joined")
  }

  "Metadata system" must {

    "join cluster" in within(10.seconds) {
      join(node1, node1)
      join(node2, node1)

      awaitAssert{
        cluster.state.members.count(_.status == MemberStatus.Up) shouldBe 2
      }

      enterBarrier("joined")
    }

    "add location from different nodes" in within(10.seconds) {

      runOn(node1) {
        val selfMember = cluster.selfMember
        val nodeName   = s"${selfMember.address.host.getOrElse("noHost")}_${selfMember.address.port.getOrElse(2552)}"

        val metadataCoordinator = Await.result(
          system.actorSelection(s"/user/guardian_$nodeName/metadata-coordinator_$nodeName").resolveOne(5.seconds),
          5.seconds)

        awaitAssert {
          metadataCoordinator ! AddLocation("db", "namespace", Location("metric", "node-1", 0, 1))
          expectMsg(LocationsAdded("db", "namespace", Seq(Location("metric", "node-1", 0, 1))))
        }
      }

      enterBarrier("after-add-locations")
    }

    "add metric info from different nodes" in within(10.seconds) {

      val metricInfo = MetricInfo("db", "namespace","metric", 100, 30)

      runOn(node1) {
        val selfMember = cluster.selfMember
        val nodeName   = s"${selfMember.address.host.getOrElse("noHost")}_${selfMember.address.port.getOrElse(2552)}"

        val metadataCoordinator = system.actorSelection(s"user/guardian_$nodeName/metadata-coordinator_$nodeName")

        awaitAssert {
          metadataCoordinator ! PutMetricInfo(metricInfo)
          expectMsg(MetricInfoPut(metricInfo))
        }
      }

      enterBarrier("after-add-metrics-info")
    }
  }
}
