package io.github.sullis.alpakka.jms

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.alpakka.jms.{JmsConsumerSettings, JmsProducerSettings, TxEnvelope}
import akka.stream.alpakka.jms.scaladsl.{JmsConsumer, JmsConsumerControl, JmsProducer}
import akka.stream.scaladsl.{Keep, RunnableGraph, Source}
import javax.jms.TextMessage
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Seconds, Span}
import jmstestkit.{JmsBroker, JmsQueue}

import scala.concurrent.Future

class JmsExampleSpec extends WordSpec
  with Matchers
  with Eventually {

  implicit override val patienceConfig = PatienceConfig(Span(5, Seconds), Span(15, Milliseconds))

  "Alpakka JMS" should {
    "happy path" in {
      val actorSys = ActorSystem.apply(UUID.randomUUID.toString)
      val materializer = buildActorMaterializer(actorSys)

      val broker = JmsBroker()
      val sourceQueue = new JmsQueue(broker)
      val destinationQueue = new JmsQueue(broker)

      val runnable = buildRunnableGraph(sourceQueue, destinationQueue, actorSys, materializer)
      runnable.run()(materializer)

      sourceQueue.size shouldBe 0
      destinationQueue.size shouldBe 0

      sourceQueue.publishMessage("a")
      sourceQueue.publishMessage("b")
      sourceQueue.publishMessage("c")

      eventually {
        sourceQueue.size shouldBe 0
        destinationQueue.size shouldBe 3
        destinationQueue.toSeq shouldBe Seq("a", "b", "c")
      }

      broker.stop()
      actorSys.terminate()
    }

  }

  private def buildJmsSource(consumerSettings: JmsConsumerSettings): Source[TextMessage, JmsConsumerControl] = {
    JmsConsumer.txSource(consumerSettings)
      .map( txEnvelope => {
        val tmsg = txEnvelope.message.asInstanceOf[javax.jms.TextMessage]
        System.out.println("Hello: " + tmsg.getText)
        txEnvelope.commit()
        tmsg
      })
  }

  private def buildRunnableGraph(
                     sourceQ: JmsQueue,
                     destinationQ: JmsQueue,
                     actorSys: ActorSystem,
                     materializer: ActorMaterializer): RunnableGraph[(JmsConsumerControl, Future[Done])] = {

    val consumerSettings = JmsConsumerSettings(actorSys, sourceQ.createConnectionFactory).withQueue(sourceQ.queueName)
    val source = buildJmsSource(consumerSettings)

    val producerSettings = JmsProducerSettings(actorSys, destinationQ.createConnectionFactory).withQueue(destinationQ.queueName)
    val sink = JmsProducer.textSink(producerSettings)

    source
      .map {
        case t: javax.jms.TextMessage => t.getText
        case other => sys.error("unexpected message type: " + other.getClass.getName)
      }
      .toMat(sink)(Keep.both)
  }

  private def buildActorMaterializer(system: ActorSystem): ActorMaterializer = {
    val decider: Supervision.Decider = ex => Supervision.Stop
    val matSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
    ActorMaterializer(matSettings)(system)
  }
}
