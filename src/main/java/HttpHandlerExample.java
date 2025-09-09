package example.httphandler;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public class HttpHandlerExample implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Burp HTTP Handler");

        api.http().registerHttpHandler(new MyHttpHandler(api));
    }
}
