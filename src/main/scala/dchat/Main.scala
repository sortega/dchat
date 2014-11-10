package dchat

import java.net.InetAddress
import scala.collection.JavaConverters._
import scala.util.Random

import net.tomp2p.connection.Bindings
import net.tomp2p.p2p.{Peer, PeerMaker}
import net.tomp2p.peers.Number160
import net.tomp2p.storage.Data

object Main {
  val Port = 2222

  def main(args: Array[String]): Unit = {
    val peer = args match {
      case Array() => startNetwork()
      case peers => joinNetwork(peers)
    }
    peer.put(peer.getPeerID)
      .setData(new Data(peer.getPeerAddress.toByteArray))
      .start()
      .awaitUninterruptibly()
    try {
      new Chat(peer).run()
    } finally {
      peer.shutdown()
    }
  }

  private def startNetwork(): Peer = {
    val peer = new PeerMaker(new Number160(Random.nextLong()))
      .setBindings(new Bindings("en0"))
      .setPorts(Port)
      .makeAndListen()
    peer.bootstrap()
      .setBootstrapTo(Seq(peer.getPeerAddress).asJava)
      .start()
      .awaitUninterruptibly()
    peer
  }

  private def joinNetwork(hostnames: Seq[String]): Peer = {
    val peer = new PeerMaker(new Number160(Random.nextLong()))
      .setBindings(new Bindings("en0"))
      .setPorts(2000 + Random.nextInt(3000))
      .makeAndListen()
    peer.getConfiguration.setBehindFirewall(true)
    val discover = peer.discover()
      .setInetAddress(InetAddress.getByName(hostnames.head))
      .setPorts(Port)
      .start()
    discover.awaitUninterruptibly()
    require(discover.isSuccess)
    val bootstrap = peer.bootstrap()
      .setPeerAddress(discover.getReporter)
      .start()
    bootstrap.awaitUninterruptibly()
    println("Bootstrapped to " + bootstrap.getBootstrapTo.asScala.mkString(", "))
    peer
  }
}
