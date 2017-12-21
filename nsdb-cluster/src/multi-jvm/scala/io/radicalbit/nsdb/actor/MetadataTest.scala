package io.radicalbit.nsdb.actor

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Count
import akka.cluster.{Cluster, MemberStatus}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.{ImplicitSender, TestProbe}
import com.typesafe.config.ConfigFactory
import io.radicalbit.nsdb.cluster.actor.MetadataCoordinator.commands.{AddLocation, GetLocations}
import io.radicalbit.nsdb.cluster.actor.MetadataCoordinator.events.{LocationAdded, LocationsGot}
import io.radicalbit.nsdb.cluster.actor.{ClusterListener, MetadataCoordinator, ReplicatedMetadataCache}
import io.radicalbit.nsdb.cluster.index.Location
import io.radicalbit.rtsae.STMultiNodeSpec

import scala.concurrent.duration._

object MetadataTest extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = ERROR
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    nsdb.index.base-path = "target/test_index"
    nsdb.write-coordinator.timeout = 5 seconds
    """))
}

class MetadataTestMultiJvmNode1 extends MetadataTest

class MetadataTestMultiJvmNode2 extends MetadataTest

class MetadataTest extends MultiNodeSpec(MetadataTest) with STMultiNodeSpec with ImplicitSender {

  import MetadataTest._

  override def initialParticipants = roles.size

  val cluster = Cluster(system)

  val clusterListener = system.actorOf(Props[ClusterListener])

  val metadataCache = system.actorOf(Props[ReplicatedMetadataCache])

  val mediator = DistributedPubSub(system).mediator

  val metadataCoordinator = system.actorOf(MetadataCoordinator.props(metadataCache), name = "metadata-coordinator")

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

      awaitAssert {
        mediator ! Count
        expectMsg(2)
      }

      enterBarrier("after-1")
    }

    "add location from different nodes" in within(10.seconds) {

      val probe     = TestProbe()
      val addresses = cluster.state.members.filter(_.status == MemberStatus.Up).map(_.address)

      runOn(node1) {
        metadataCoordinator.tell(AddLocation("namespace", Location("metric", "node-1", 0, 1), 0), probe.ref)
        probe.expectMsg(LocationAdded("namespace", Location("metric", "node-1", 0, 1), 0))
      }

      awaitAssert {
        addresses.foreach(a => {
          val metadataActor =
            system.actorSelection(s"user/metadata_${a.host.getOrElse("noHost")}_${a.port.getOrElse(2552)}")
          metadataActor.tell(GetLocations("namespace", "metric", 0), probe.ref)
          probe.expectMsg(LocationsGot("namespace", "metric", Seq(Location("metric", "node-1", 0, 1)), 0))
        })
      }

      enterBarrier("after-add")

    }
  }
}