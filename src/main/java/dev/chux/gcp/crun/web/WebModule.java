package dev.chux.gcp.crun.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;

import javax.servlet.ServletConfig;

@EnableAutoConfiguration
@ServletComponentScan
@Configuration
@ComponentScan
public class WebModule { }
