package io.github.sullis.alpakka.jms

import java.util.UUID

import akka.Done
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, OverflowStrategy, Supervision}
import akka.stream.alpakka.jms.{JmsConsumerSettings, JmsPassThrough, JmsProducerSettings, JmsTextMessage}
import org.scalatest.{Matchers, WordSpec}
import jmstestkit.{JmsBroker, JmsQueue}
import akka.stream.alpakka.jms.scaladsl.{JmsConsumer, JmsConsumerControl, JmsProducer}
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Seconds, Span}

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

      sourceQueue.size shouldBe 0
      destinationQueue.size shouldBe 0

      val runnable = buildRunnableGraph(sourceQueue, destinationQueue, actorSys, materializer)
      runnable.run()(materializer)

      sourceQueue.size shouldBe 0
      destinationQueue.size shouldBe 0

      sourceQueue.publishMessage("a")
      sourceQueue.publishMessage("b")
      sourceQueue.publishMessage("c")

      eventually {
        destinationQueue.toSeq shouldBe Seq("a", "b", "c")
      }

      sourceQueue.size shouldBe 0
      destinationQueue.size shouldBe 3

      broker.stop()
      actorSys.terminate()
    }

  }

  private def buildRunnableGraph(
                     sourceQ: JmsQueue,
                     destinationQ: JmsQueue,
                     actorSys: ActorSystem,
                     materializer: ActorMaterializer): RunnableGraph[(JmsConsumerControl, Future[Done])] = {

    val consumerSettings = JmsConsumerSettings(actorSys, sourceQ.createConnectionFactory).withQueue(sourceQ.queueName)
    val source = JmsConsumer.txSource(consumerSettings)
      .map( txEnvelope => {
        val tmsg = txEnvelope.message.asInstanceOf[javax.jms.TextMessage]
        System.out.println("Hello: " + tmsg.getText)
        txEnvelope.commit()
        tmsg
      })

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
