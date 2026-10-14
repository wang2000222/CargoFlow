package com.cargoflow.common;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(clients = Wire.class)
public class CloudInfrastructure {}
