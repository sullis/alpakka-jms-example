package io.github.sullis.alpakka.jms

import org.scalatest.{Matchers, WordSpec}
import jmstestkit.{JmsBroker, JmsQueue}

class JmsExampleSpec extends WordSpec with Matchers {

  "Alpakka JMS" should {
    "happy path" in {

      val broker1 = JmsBroker()
      val broker2 = JmsBroker()

      val queue1 = new JmsQueue(broker1)
      val queue2 = new JmsQueue(broker2)

      broker1.stop()
      broker2.stop()
    }
  }
}
