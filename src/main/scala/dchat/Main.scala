package dchat

import java.net.InetAddress
import scala.collection.JavaConverters._
import scala.util.Random

import net.tomp2p.connection.Bindings
import net.tomp2p.dht.{PeerBuilderDHT, PeerDHT}
import net.tomp2p.nat.PeerBuilderNAT
import net.tomp2p.p2p.PeerBuilder
import net.tomp2p.peers.Number160
import net.tomp2p.storage.Data

object Main {
  val Port = 2223

  def main(args: Array[String]): Unit = {
    val dht = args match {
      case Array("serve", hostname) => startNetwork(hostname)
      case Array("upnp", masterHostname) => joinNetworkWithUpnp(masterHostname)
      case Array("fw", externalAddress, forwardedPort, masterAddress) =>
        joinNetworkWithPortForwarding(externalAddress, forwardedPort.toInt, masterAddress)
      case _ =>
        println("Usage: java -jar <this jar> (serve <hostname> | upnp <master_hostname> | " +
          "fw <external_ip> <port> <master_hostname> )")
        System.exit(-1)
        return
    }
    try {
      new Chat(dht).run()
    } finally {
      dht.shutdown()
    }
  }

  private def publishAddress(peer: PeerDHT): Unit = {
    val publication = peer.put(peer.peerID)
      .data(new Data(peer.peer().peerAddress.toByteArray))
      .start()
    publication.awaitUninterruptibly()
    require(publication.isSuccess)
  }

  private def unpublishAddress(peer: PeerDHT): Unit = {
    peer.remove(peer.peer().peerID).start().awaitUninterruptibly()
  }

  private def startNetwork(hostname: String): PeerDHT = {
    val peer = new PeerBuilder(new Number160(Random.nextLong()))
      //.bindings(new Bindings().addAddress(InetAddress.getByName(hostname)))
      .ports(Port)
      .start()
    peer.bootstrap()
      .bootstrapTo(Seq(peer.peerAddress()).asJava)
      .start()
      .awaitUninterruptibly()
    new PeerBuilderDHT(peer).start()
  }

  private def joinNetworkWithUpnp(masterHostname: String): PeerDHT = {
    val peer = new PeerBuilder(new Number160(Random.nextLong()))
      .ports(5000 + Random.nextInt(1000))
      .behindFirewall(true)
      .start()
    val dht = new PeerBuilderDHT(peer).start()
    val discover = dht.peer().discover()
      .inetAddress(InetAddress.getByName(masterHostname))
      .ports(Port)
      .start()
    val nat = new PeerBuilderNAT(peer).start()
    val forwarding = nat.startSetupPortforwarding(discover)
    discover.awaitUninterruptibly()
    forwarding.awaitUninterruptibly()
    require(forwarding.isSuccess, "FORWARDING FAILURE: " + forwarding.failedReason())

    val bootstrap = dht.peer().bootstrap()
      .peerAddress(forwarding.reporter)
      .start()
    bootstrap.awaitUninterruptibly()
    require(bootstrap.isSuccess, "BOOTSTRAP FAILURE: " + bootstrap.failedReason())

    println("Bootstrapped to %s (%d peers)".format(
      bootstrap.bootstrapTo.asScala.mkString(", "), dht.peerBean.peerMap.all.size()))
    println("Peers: " + dht.peerBean.peerMap.all.asScala.mkString(", "))
    val address: InetAddress = dht.peerBean.serverPeerAddress.inetAddress
    if (!address.isLoopbackAddress && !address.isSiteLocalAddress) {
      publishAddress(dht)
    } else {
      unpublishAddress(dht)
    }
    dht
  }

  private def joinNetworkWithPortForwarding(
      externalAddress: String, forwardedPort: Int, masterAddress: String): PeerDHT = {
    val peer = new PeerBuilder(new Number160(Random.nextLong()))
      .bindings(new Bindings().addAddress(InetAddress.getByName(externalAddress)))
      .ports(forwardedPort)
      .start()
    val bootstrap = peer.bootstrap()
      .inetAddress(InetAddress.getByName(masterAddress))
      .ports(Port)
      .start()
    bootstrap.awaitUninterruptibly()
    val dht = new PeerBuilderDHT(peer).start()
    publishAddress(dht)
    println("Bootstrapped to " + bootstrap.bootstrapTo.asScala.mkString(", "))
    dht
  }
}
