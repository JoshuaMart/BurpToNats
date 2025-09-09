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
                try {
                    Properties properties = new Properties();
                    properties.load(new FileInputStream(configPath));
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

            natsConnection = Nats.connect(natsUrl);
            api.logging().logToOutput("Connected to NATS: " + natsUrl);
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

        if (natsConnection != null) {
            try {
                natsConnection.publish("burp", requestToBeSent.toString().getBytes());
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
