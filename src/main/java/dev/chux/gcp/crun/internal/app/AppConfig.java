package dev.chux.gcp.crun.internal.app;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@Configuration
@ComponentScan("dev.chux.gcp.crun.internal.app")
@EnableWebMvc
@EnableAutoConfiguration
public class AppConfig { }
