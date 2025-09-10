package example.httphandler;

import burp.api.montoya.MontoyaApi;
import io.nats.client.Nats;
import io.nats.client.Connection;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpHeader;
import java.util.List;
import java.util.Properties;
import java.io.FileInputStream;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.Paths;
import io.nats.client.Options;

import static burp.api.montoya.http.handler.RequestToBeSentAction.continueWith;
import static burp.api.montoya.http.handler.ResponseReceivedAction.continueWith;
import static burp.api.montoya.http.message.params.HttpParameter.urlParameter;

class MyHttpHandler implements HttpHandler {
    private Connection natsConnection;
    private MontoyaApi api;

    public MyHttpHandler(MontoyaApi api) {
        this.api = api;

        try {
            String natsUrl = "nats://localhost:4222";

            String currentDir = System.getProperty("user.dir");
            api.logging().logToOutput("Current working directory: " + currentDir);

            String[] configPaths = {
                System.getProperty("user.home") + "/nats.properties",
                System.getProperty("user.home") + "/.burp/nats.properties"
            };

            boolean configLoaded = false;
            for (String configPath : configPaths) {
                try (FileInputStream fis = new FileInputStream(configPath)) {
                    Properties properties = new Properties();
                    properties.load(fis);
                    natsUrl = properties.getProperty("natsUrl", natsUrl);
                    api.logging().logToOutput("Loaded NATS config from: " + configPath);
                    configLoaded = true;
                    break;
                } catch (Exception e) {
                    api.logging().logToOutput("Config not found at: " + configPath);
                }
            }

            if (!configLoaded) {
                api.logging().logToOutput("No config file found, using default NATS URL: " + natsUrl);
            }

            String[] credsPaths = {
                System.getProperty("user.home") + "/nats.creds",
                System.getProperty("user.home") + "/.burp/nats.creds"
            };

            String credsPath = null;
            for (String p : credsPaths) {
                if (new File(p).exists()) {
                    credsPath = p;
                    break;
                }
            }

           if (credsPath != null) {
                api.logging().logToOutput("Using creds file: " + credsPath);
                Options options = new Options.Builder()
                        .server(natsUrl)
                        .authHandler(Nats.credentials(credsPath))
                        .build();
                natsConnection = Nats.connect(options);
            } else {
                api.logging().logToOutput("No creds file found, using unauthenticated connection.");
                natsConnection = Nats.connect(natsUrl);
            }

            api.logging().logToOutput("Connected to NATS: " + natsUrl + (credsPath != null ? " (with creds)" : ""));
        } catch (Exception e) {
            api.logging().logToError("Failed to connect to NATS: " + e.getMessage());
        }
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        String url = requestToBeSent.url();
        List<HttpHeader> headers = requestToBeSent.headers();
        String body = requestToBeSent.body().toString();
        String method = requestToBeSent.method();
        String httpVersion = requestToBeSent.httpVersion();

        Map<String, String> headersMap = new HashMap<>();
        for (HttpHeader header : headers) {
            headersMap.put(header.name(), header.value());
        }

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("url", url);
        requestData.put("method", method);
        requestData.put("httpVersion", httpVersion);
        requestData.put("body", body);
        requestData.put("headers", headersMap);
        String requestJson = gson.toJson(requestData);

        if (natsConnection != null) {
            try {
                natsConnection.publish("burp", requestJson.getBytes());
                api.logging().logToOutput("Sent request to NATS");
            }
            catch (Exception e) {
                api.logging().logToError("Failed to send request to NATS: " + e.getMessage());
            }
        }

        return continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return continueWith(responseReceived);
    }
}
