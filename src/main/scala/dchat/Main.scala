package dchat

import java.net.InetAddress
import scala.collection.JavaConverters._
import scala.util.Random

import net.tomp2p.connection.Bindings
import net.tomp2p.futures.FutureDHT
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.Number160
import net.tomp2p.storage.Data

object Main {
  val Port = 2222

  def main(args: Array[String]): Unit = {
    val peer = args match {
      case Array("serve", hostname) => startNetwork(hostname)
      case Array("upnp", masterHostname) => joinNetworkWithUpnp(masterHostname)
      case Array("fw", externalAddress, forwardedPort, masterAddress) =>
        joinNetworkWithPortForwarding(externalAddress, forwardedPort.toInt, masterAddress)
    }
    try {
      new Chat(peer).run()
    } finally {
      peer.shutdown()
    }
  }

  private def publishAddress(peer: Peer): Unit = {
    val publication = peer.put(peer.getPeerID)
      .setData(new Data(peer.getPeerAddress.toByteArray))
      .start()
    publication.awaitUninterruptibly()
    require(publication.isSuccess)
  }

  private def unpublishAddress(peer: Peer): FutureDHT = {
    peer.remove(peer.getPeerID).start().awaitUninterruptibly()
  }

  private def startNetwork(hostname: String): Peer = {
    val peer = new PeerMaker(new Number160(Random.nextLong()))
      .setBindings(new Bindings(InetAddress.getByName(hostname), Port, Port))
      .setPorts(Port)
      .makeAndListen()
    peer.bootstrap()
      .setBootstrapTo(Seq(peer.getPeerAddress).asJava)
      .start()
      .awaitUninterruptibly()
    peer
  }

  private def joinNetworkWithUpnp(masterHostname: String): Peer = {
    val peer = new PeerMaker(new Number160(Random.nextLong()))
      .setPorts(5000 + Random.nextInt(1000))
      .makeAndListen()
    peer.getConfiguration.setBehindFirewall(true)
    val discover = peer.discover()
      .setInetAddress(InetAddress.getByName(masterHostname))
      .setPorts(Port)
      .start()
    discover.awaitUninterruptibly()
    require(discover.isSuccess)
    val bootstrap = peer.bootstrap()
      .setPeerAddress(discover.getReporter)
      .start()
    bootstrap.awaitUninterruptibly()
    println("Bootstrapped to %s (%d peers)".format(
      bootstrap.getBootstrapTo.asScala.mkString(", "), peer.getPeerBean.getPeerMap.getAll.size()))
    println("Peers: " + peer.getPeerBean.getPeerMap.getAll.asScala.mkString(", "))
    val address: InetAddress = peer.getPeerBean.getServerPeerAddress.getInetAddress
    if (!address.isLoopbackAddress && !address.isSiteLocalAddress) {
      publishAddress(peer)
    } else {
      unpublishAddress(peer)
    }
    peer
  }

  private def joinNetworkWithPortForwarding(
      externalAddress: String, forwardedPort: Int, masterAddress: String): Peer = {
    val peer = new PeerMaker(new Number160(Random.nextLong()))
      .setBindings(new Bindings(InetAddress.getByName(externalAddress), forwardedPort, forwardedPort))
      .setPorts(forwardedPort)
      .makeAndListen()
    val bootstrap = peer.bootstrap()
      .setInetAddress(InetAddress.getByName(masterAddress))
      .setPorts(Port)
      .start()
    bootstrap.awaitUninterruptibly()
    publishAddress(peer)
    println("Bootstrapped to " + bootstrap.getBootstrapTo.asScala.mkString(", "))
    peer
  }
}
