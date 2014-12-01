package dchat

import java.io.PrintWriter
import scala.annotation.tailrec

import jline.console.ConsoleReader
import net.tomp2p.dht.{FutureSend, PeerDHT}
import net.tomp2p.futures.{BaseFutureListener, FutureDirect}
import net.tomp2p.peers.{Number160, PeerAddress, PeerMapChangeListener, PeerStatatistic}
import net.tomp2p.rpc.ObjectDataReply

class Chat(dht: PeerDHT) {
  private val console = new ConsoleReader()
  console.setPrompt(dht.peerID() + ">> ")
  private val output = new PrintWriter(console.getOutput)

  object IncomingDataListener extends ObjectDataReply {
    override def reply(peerAddress: PeerAddress, payload: scala.Any): AnyRef = {
      val message = payload.asInstanceOf[Array[Byte]]
      output.println(s"<<${peerAddress.peerId}: ${new String(message)}")
      null
    }
  }

  object PeerChangeListener extends PeerMapChangeListener {
    override def peerInserted(peerAddress: PeerAddress, verified: Boolean): Unit = {
      output.println("-- New peer at " + peerAddress)
    }

    override def peerRemoved(peerAddress: PeerAddress, stats: PeerStatatistic): Unit = {
      output.println("-- Removed peer at " + peerAddress)
    }

    override def peerUpdated(peerAddress: PeerAddress, stats: PeerStatatistic): Unit = {}
  }

  def run(): Unit = {
    dht.peer().objectDataReply(IncomingDataListener)
    dht.peerBean.peerMap.addPeerMapChangeListener(PeerChangeListener)
    output.println("Started as peer " + dht.peerID())
    loop()
  }

  @tailrec
  private def loop(): Unit = {
    val line = console.readLine()
    if (line != null) {
      processCommand(line)
      loop()
    }
  }

  private val SendDirectPattern = "send-direct (\\S+) (.*)".r
  private val SendPattern = "send (\\S+) (.*)".r
  private val EmptyPattern = "\\s*".r

  private def processCommand(line: String): Unit = line match {
    case SendDirectPattern(to, message) => sendDirectMessage(to, message)
    case SendPattern(to, message) => sendMessage(to, message)
    case EmptyPattern() => // Do nothing
    case unknown => output.println(s"Don't understand '$unknown'")
  }

  private def sendDirectMessage(to: String, message: String): Unit = {
    val futureGet = dht.get(new Number160(to)).start()
    futureGet.awaitUninterruptibly()
    if (futureGet.isFailed || futureGet.data == null) {
      output.println(s"(cannot resolve $to address)")
      return
    }
    val address = new PeerAddress(futureGet.data.toBytes())
    val s = dht.peer()
      .sendDirect(address)
      .`object`(message.getBytes)
      .start()
    s.addListener(new BaseFutureListener[FutureDirect] {
      override def operationComplete(f: FutureDirect): Unit = {
        output.println(s"(sent to $address)")
      }
      override def exceptionCaught(throwable: Throwable): Unit = {
        output.println(s"(cannot send: $throwable)")
      }
    })
  }

  private def sendMessage(to: String, message: String): Unit = {
    val toId: Number160 = new Number160(to)
    val s = dht.send(toId)
      .`object`(message.getBytes)
      .start()
    s.addListener(new BaseFutureListener[FutureSend] {
      override def operationComplete(f: FutureSend): Unit = {
        output.println(s"(sent)")
      }
      override def exceptionCaught(throwable: Throwable): Unit = {
        output.println(s"(cannot send: $throwable)")
      }
    })
  }
}
