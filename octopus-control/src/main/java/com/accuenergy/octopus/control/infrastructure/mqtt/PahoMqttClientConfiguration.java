package com.accuenergy.octopus.control.infrastructure.mqtt;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "octopus.mqtt", name = "enabled", havingValue = "true")
public class PahoMqttClientConfiguration {
    @Bean
    MqttAsyncClient mqttAsyncClient(
            @Value("${octopus.mqtt.host}") String host,
            @Value("${octopus.mqtt.port}") int port,
            @Value("${octopus.mqtt.client-id}") String clientId,
            @Value("${octopus.mqtt.persistence-directory:/tmp/octopus-mqtt}") String persistenceDirectory,
            @Value("${octopus.mqtt.tls.enabled:false}") boolean tlsEnabled) throws Exception {
        String serverUri = (tlsEnabled ? "ssl" : "tcp") + "://" + host + ":" + port;
        return new MqttAsyncClient(serverUri, clientId, new MqttDefaultFilePersistence(persistenceDirectory));
    }

    @Bean
    MqttConnectOptions mqttConnectOptions(
            @Value("${octopus.mqtt.tls.enabled:false}") boolean tlsEnabled,
            @Value("${octopus.mqtt.tls.key-store:}") String keyStorePath,
            @Value("${octopus.mqtt.tls.key-store-password:}") String keyStorePassword,
            @Value("${octopus.mqtt.tls.trust-store:}") String trustStorePath,
            @Value("${octopus.mqtt.tls.trust-store-password:}") String trustStorePassword,
            @Value("${octopus.mqtt.username:}") String username,
            @Value("${octopus.mqtt.password:}") String password,
            @Value("${octopus.mqtt.keep-alive-seconds:30}") int keepAliveSeconds,
            @Value("${octopus.mqtt.max-inflight:1000}") int maxInflight) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(false);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(keepAliveSeconds);
        options.setMaxInflight(maxInflight);
        if (!username.isBlank()) options.setUserName(username);
        if (!password.isBlank()) options.setPassword(password.toCharArray());
        if (tlsEnabled) options.setSocketFactory(sslContext(keyStorePath, keyStorePassword,
                trustStorePath, trustStorePassword).getSocketFactory());
        return options;
    }

    private static SSLContext sslContext(String keyStorePath, String keyStorePassword,
                                         String trustStorePath, String trustStorePassword) {
        if (keyStorePath.isBlank() || trustStorePath.isBlank()) {
            throw new IllegalStateException("MQTT mTLS requires key-store and trust-store");
        }
        try {
            KeyStore keyStore = loadStore(keyStorePath, keyStorePassword);
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, keyStorePassword.toCharArray());
            KeyStore trustStore = loadStore(trustStorePath, trustStorePassword);
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
            return context;
        } catch (Exception invalidTlsMaterial) {
            throw new IllegalStateException("Unable to load MQTT TLS material", invalidTlsMaterial);
        }
    }

    private static KeyStore loadStore(String path, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            store.load(input, password.toCharArray());
        }
        return store;
    }
}
