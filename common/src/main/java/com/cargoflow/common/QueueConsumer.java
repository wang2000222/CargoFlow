package com.cargoflow.common;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cf.mq", havingValue = "true")
public class QueueConsumer {
  final Peers peers;
  final String name, secret, port;

  public QueueConsumer(
      Peers peers,
      @Value("${spring.application.name}") String name,
      @Value("${cf.secret}") String secret,
      @Value("${server.port}") String port) {
    this.peers = peers;
    this.name = name;
    this.secret = secret;
    this.port = port;
  }

  @Bean
  public Declarables eventTopology() {
    DirectExchange events = new DirectExchange("cargoflow.events", true, false);
    DirectExchange dead = new DirectExchange("cargoflow.dead", true, false);
    Queue queue =
        QueueBuilder.durable(name + ".events")
            .deadLetterExchange("cargoflow.dead")
            .deadLetterRoutingKey(name)
            .build();
    Queue dlq = QueueBuilder.durable(name + ".dead").build();
    return new Declarables(
        events,
        dead,
        queue,
        dlq,
        BindingBuilder.bind(queue).to(events).with(name),
        BindingBuilder.bind(dlq).to(dead).with(name));
  }

  @RabbitListener(queues = "${spring.application.name}.events")
  public void receive(Message message) {
    Map<String, Object> envelope = Json.read(new String(message.getBody(), StandardCharsets.UTF_8));
    String payload = (String) envelope.get("payload");
    if (!Crypto.equal((String) envelope.get("signature"), Crypto.sign(secret, "event\n" + payload)))
      throw new IllegalArgumentException("Invalid event signature");
    Map<String, Object> data = Json.read(payload);
    String path = (String) data.get("path");
    if (!name.equals(data.get("target")) || !path.startsWith("/internal/"))
      throw new IllegalArgumentException("Invalid event target");
    peers.call(
        "http://127.0.0.1:" + port,
        path,
        "POST",
        (String) data.get("body"),
        new Actor((String) data.get("tenant"), "event-consumer", "SYSTEM"),
        (String) data.get("trace"));
  }
}
