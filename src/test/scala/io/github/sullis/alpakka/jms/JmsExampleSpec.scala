package io.github.sullis.alpakka.jms

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.alpakka.jms.{JmsConsumerSettings, JmsProducerSettings, TxEnvelope}
import akka.stream.alpakka.jms.scaladsl.{JmsConsumer, JmsConsumerControl, JmsProducer}
import akka.stream.scaladsl.{Keep, RestartSink, RestartSource, RunnableGraph, Source}
import javax.jms.TextMessage
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Milliseconds, Seconds, Span}
import jmstestkit.{JmsBroker, JmsQueue}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class JmsExampleSpec extends WordSpec
  with Matchers
  with Eventually {

  implicit override val patienceConfig = PatienceConfig(Span(5, Seconds), Span(15, Milliseconds))

  private val minBackoff = FiniteDuration(5, TimeUnit.SECONDS)
  private val maxBackoff = FiniteDuration(1, TimeUnit.MINUTES)
  private val randomFactor = 2.0d

  "Alpakka JMS" should {
    "happy path" in {
      val actorSys = ActorSystem.apply(UUID.randomUUID.toString)
      val materializer = buildActorMaterializer(actorSys)

      val broker = JmsBroker()
      val jmsConnFactory = broker.createConnectionFactory
      val sourceQueue = new JmsQueue(broker)
      val destinationQueue = new JmsQueue(broker)

      val runnable = buildRunnableGraph(jmsConnFactory, sourceQueue.queueName, destinationQueue.queueName, actorSys, materializer)
      runnable.run()(materializer)

      sourceQueue.size shouldBe 0
      destinationQueue.size shouldBe 0

      sourceQueue.publishMessage("a")
      sourceQueue.publishMessage("b")

      broker.closeClientConnections()

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

  def buildRestartSource(sourceFactory: () => Source[TextMessage, JmsConsumerControl]): Source[TextMessage, NotUsed] = {
    RestartSource.withBackoff(
      minBackoff = minBackoff,
      maxBackoff = maxBackoff,
      randomFactor = randomFactor
    )(sourceFactory)
  }

  private def buildRunnableGraph(
                     jmsConnFactory: javax.jms.ConnectionFactory,
                     sourceQName: String,
                     destinationQName: String,
                     actorSys: ActorSystem,
                     materializer: ActorMaterializer): RunnableGraph[NotUsed] = {

    val consumerSettings = JmsConsumerSettings(actorSys, jmsConnFactory).withQueue(sourceQName)
    val source = buildRestartSource( () => buildJmsSource(consumerSettings))

    val producerSettings = JmsProducerSettings(actorSys, jmsConnFactory).withQueue(destinationQName)
    val sink = RestartSink.withBackoff(
      minBackoff = minBackoff,
      maxBackoff = maxBackoff,
      randomFactor = randomFactor)(
      () => JmsProducer.textSink(producerSettings))

    source
      .map {
        case t: javax.jms.TextMessage => t.getText
        case other => sys.error("unexpected message type: " + other.getClass.getName)
      }
      .toMat(sink)(Keep.none)
  }

  private def buildActorMaterializer(system: ActorSystem): ActorMaterializer = {
    val decider: Supervision.Decider = ex => Supervision.Stop
    val matSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
    ActorMaterializer(matSettings)(system)
  }
}
