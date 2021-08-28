package io.immutables.micro.kafka;

import java.util.Properties;
import org.apache.kafka.clients.CommonClientConfigs;
import static org.apache.kafka.clients.CommonClientConfigs.*;

@Deprecated(forRemoval = true)
public class EventHub {
  public static Properties addProperties(Properties p) {
    p.setProperty(BOOTSTRAP_SERVERS_CONFIG, "roboware.servicebus.windows.net:9093");
    p.setProperty(SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
    p.setProperty("sasl.mechanism", "PLAIN");
    p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.plain.PlainLoginModule required" +
        " username=\"$ConnectionString\"" +
        " password=\"Endpoint=sb://roboware.servicebus.windows.net/;SharedAccessKeyName=RobowareApplication;SharedAccessKey=Bw9mQiUQ1y058f2JnrAnxctZCGSOLNpsan3BfsEqxRo=\";");
    return p;
  }
}
