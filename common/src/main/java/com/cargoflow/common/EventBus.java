package com.cargoflow.common;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Publisher confirms cover broker acceptance; business delivery is eventually consistent. */
@Component
public class EventBus {
  final Peers peers;
  final RabbitTemplate rabbit;
  final boolean mq;
  final String secret;

  public EventBus(
      Peers peers,
      RabbitTemplate rabbit,
      @Value("${cf.mq:false}") boolean mq,
      @Value("${cf.secret}") String secret) {
    this.peers = peers;
    this.rabbit = rabbit;
    this.mq = mq;
    this.secret = secret;
  }

  public void send(
      String target, String base, String path, String body, Actor actor, String trace, String id) {
    if (!mq) {
      peers.call(base, path, "POST", body, actor, trace);
      return;
    }
    String payload =
        Json.write(
            Map.of(
                "target",
                target,
                "path",
                path,
                "body",
                body,
                "tenant",
                actor.tenant(),
                "trace",
                trace,
                "id",
                id));
    String envelope =
        Json.write(
            Map.of("payload", payload, "signature", Crypto.sign(secret, "event\n" + payload)));
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setMessageId(id);
    CorrelationData correlation = new CorrelationData(id);
    rabbit.setMandatory(true);
    try {
      rabbit.send(
          "cargoflow.events",
          target,
          new Message(envelope.getBytes(StandardCharsets.UTF_8), properties),
          correlation);
      if (!correlation.getFuture().get(3, TimeUnit.SECONDS).isAck()
          || correlation.getReturned() != null)
        throw new IllegalStateException("Broker did not accept routed message");
    } catch (Exception e) {
      throw new Problem(503, "BROKER_UNAVAILABLE", "消息未获确认，保留待重试");
    }
  }
}
