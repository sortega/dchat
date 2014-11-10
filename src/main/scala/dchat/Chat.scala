package dchat

import java.io.PrintWriter
import scala.annotation.tailrec

import jline.console.ConsoleReader
import net.tomp2p.futures.{FutureDHT, BaseFutureListener, FutureResponse}
import net.tomp2p.p2p.Peer
import net.tomp2p.peers.{Number160, PeerAddress, PeerMapChangeListener}
import net.tomp2p.rpc.ObjectDataReply

class Chat(peer: Peer) {
  private val console = new ConsoleReader()
  console.setPrompt(peer.getPeerID + ">> ")
  private val output = new PrintWriter(console.getOutput)

  object IncomingDataListener extends ObjectDataReply {
    override def reply(peerAddress: PeerAddress, payload: scala.Any): AnyRef = {
      val message = payload.asInstanceOf[Array[Byte]]
      output.println(s"<<${peerAddress.getID}: ${new String(message)}")
      null
    }
  }

  object PeerChangeListener extends PeerMapChangeListener {
    override def peerInserted(peerAddress: PeerAddress): Unit = {
      output.println("-- New peer at " + peerAddress)
    }
    override def peerRemoved(peerAddress: PeerAddress): Unit =  {
      output.println("-- Removed peer at " + peerAddress)
    }
    override def peerUpdated(peerAddress: PeerAddress): Unit = {}
  }

  def run(): Unit = {
    peer.setObjectDataReply(IncomingDataListener)
    peer.getPeerBean.getPeerMap.addPeerMapChangeListener(PeerChangeListener)
    output.println("Started as peer " + peer.getPeerID)
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
    val futureResult = peer.get(new Number160(to)).start()
    futureResult.awaitUninterruptibly()
    if (futureResult.getData == null) {
      output.println(s"(cannot resolve $to address)")
      return
    }
    val address = new PeerAddress(futureResult.getData.getData)
    val s = peer.sendDirect(address)
      .setObject(message.getBytes)
      .start()
    s.addListener(new BaseFutureListener[FutureResponse] {
      override def operationComplete(f: FutureResponse): Unit = {
        output.println("(sent)")
      }
      override def exceptionCaught(throwable: Throwable): Unit = {
        output.println(s"(cannot send: $throwable)")
      }
    })
  }

  private def sendMessage(to: String, message: String): Unit = {
    val toId: Number160 = new Number160(to)
    val s = peer.send(toId)
      .setObject(message.getBytes)
      .start()
    s.addListener(new BaseFutureListener[FutureDHT] {
      override def operationComplete(f: FutureDHT): Unit = {
        output.println("(sent)")
      }
      override def exceptionCaught(throwable: Throwable): Unit = {
        output.println(s"(cannot send: $throwable)")
      }
    })
  }
}
