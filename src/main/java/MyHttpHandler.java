package example.httphandler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import io.nats.client.JetStreamApiException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


import static burp.api.montoya.http.handler.RequestToBeSentAction.continueWith;
import static burp.api.montoya.http.handler.ResponseReceivedAction.continueWith;

class MyHttpHandler implements HttpHandler {

    private static final String DEFAULT_NATS_URL = "nats://localhost:4222";
    private static final String STREAM_SUBJECT = "fuzzr";

    private static final String[] DEFAULT_CONFIG_PATHS = {
            System.getProperty("user.home") + "/nats.properties",
            System.getProperty("user.home") + "/.burp/nats.properties"
    };
    private static final String[] DEFAULT_CREDS_PATHS = {
            System.getProperty("user.home") + "/nats.creds",
            System.getProperty("user.home") + "/.burp/nats.creds"
    };

    private final MontoyaApi api;
    private final Gson gson;
    private final Connection nats;
    private JetStream jetStream;

    MyHttpHandler(MontoyaApi api) {
        this.api = api;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.nats = initNats();
    }

    private Connection initNats() {
        try {
            api.logging().logToOutput("Current working directory: " + System.getProperty("user.dir"));

            final String natsUrl = loadNatsUrlOrDefault(DEFAULT_CONFIG_PATHS, DEFAULT_NATS_URL);
            final String credsPath = findFirstExisting(DEFAULT_CREDS_PATHS);

            final Options.Builder builder = new Options.Builder()
                    .server(natsUrl)
                    .connectionTimeout(Duration.ofSeconds(3))
                    .pingInterval(Duration.ofSeconds(15))
                    .maxPingsOut(2)
                    .reconnectWait(Duration.ofSeconds(1))
                    .maxReconnects(-1)
                    .errorListener(new SimpleNatsErrorListener(api))
                    .connectionListener((conn, type) ->
                            api.logging().logToOutput("NATS connection event: " + type));

            if (credsPath != null) {
                api.logging().logToOutput("Using NATS creds: " + credsPath);
                builder.authHandler(Nats.credentials(credsPath));
            } else {
                api.logging().logToOutput("No creds file found, using unauthenticated connection.");
            }

            Connection c = Nats.connect(builder.build());
            jetStream = c.jetStream();

            api.logging().logToOutput("Connected to NATS at " + natsUrl + (credsPath != null ? " (creds)" : ""));
            return c;
        } catch (Exception e) {
            api.logging().logToError("Failed to connect to NATS: " + e.getMessage());
            return null;
        }
    }

    private static String loadNatsUrlOrDefault(String[] paths, String fallback) {
        for (String path : paths) {
            try (FileInputStream fis = new FileInputStream(path)) {
                Properties p = new Properties();
                p.load(fis);
                String url = p.getProperty("natsUrl");
                if (url != null && !url.isBlank()) {
                    return url.trim();
                }
            } catch (Exception ignored) {
                // silence: testing several paths
            }
        }
        return fallback;
    }

    private static String findFirstExisting(String[] paths) {
        for (String p : paths) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent req) {
        try {
            final String url = req.url();

            final List<HttpHeader> headers = req.headers();
            final Map<String, String> headersMap = new LinkedHashMap<>(Math.max(16, headers.size()));
            for (HttpHeader h : headers) {
                // En cas de doublons, la dernière valeur l'emporte
                headersMap.put(h.name(), h.value());
            }

            final String body = req.body() != null ? req.body().toString() : "";

            final Map<String, Object> payload = new LinkedHashMap<>(8);
            payload.put("url", url);
            payload.put("method", req.method());
            payload.put("httpVersion", req.httpVersion());
            payload.put("body", body);
            payload.put("headers", headersMap);

            final byte[] bytes = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);

            if (jetStream != null) {
                try {
                    PublishAck ack = jetStream.publish(STREAM_SUBJECT, bytes);
                    api.logging().logToOutput("Sent request to NATS - Stream: " + ack.getStream() + ", Sequence: " + ack.getSeqno());
                } catch (JetStreamApiException e) {
                    api.logging().logToError("JetStream API error: " + e.getMessage());
                } catch (IOException e) {
                    api.logging().logToError("IO error sending to NATS: " + e.getMessage());
                } catch (Exception e) {
                    api.logging().logToError("Failed to send request to NATS: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            api.logging().logToError("General error processing message: " + e.getMessage());
        }

        return continueWith(req);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return continueWith(responseReceived);
    }

    /**
     *  Minimalist listener for diagnosing NATS errors
     */
    private static final class SimpleNatsErrorListener implements io.nats.client.ErrorListener {
        private final MontoyaApi api;
        SimpleNatsErrorListener(MontoyaApi api) { this.api = api; }

        @Override
        public void errorOccurred(Connection conn, String error) {
            api.logging().logToError("NATS error: " + error);
        }

        @Override
        public void exceptionOccurred(Connection conn, Exception exp) {
            api.logging().logToError("NATS exception: " + exp.getMessage());
        }
    }
}
