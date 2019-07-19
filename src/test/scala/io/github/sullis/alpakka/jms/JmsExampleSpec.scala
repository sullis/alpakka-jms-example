package io.github.sullis.alpakka.jms

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.alpakka.jms.{JmsConsumerSettings, JmsProducerSettings}
import org.scalatest.{Matchers, WordSpec}
import jmstestkit.{JmsBroker, JmsQueue}
import akka.stream.alpakka.jms.scaladsl.{JmsConsumer, JmsProducer}

class JmsExampleSpec extends WordSpec with Matchers {

  "Alpakka JMS" should {
    "happy path" in {

      val actorSys = ActorSystem.apply(UUID.randomUUID.toString)
      val broker1 = JmsBroker()
      val broker2 = JmsBroker()

      val queue1 = new JmsQueue(broker1)
      val queue2 = new JmsQueue(broker2)

      val producerSettings = JmsProducerSettings(actorSys, queue1.createConnectionFactory).withQueue(queue1.queueName)
      val sink = JmsProducer.textSink(producerSettings)

      val consumerSettings = JmsConsumerSettings(actorSys, queue1.createConnectionFactory).withQueue(queue1.queueName)
      val source = JmsConsumer.txSource(consumerSettings)

      source.map { txEnvelope =>
        System.out.println(txEnvelope.message)
        txEnvelope.commit()
      }

      broker1.stop()
      broker2.stop()
      actorSys.terminate()
    }
  }
}
