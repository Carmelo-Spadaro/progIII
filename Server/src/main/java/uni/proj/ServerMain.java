import uni.proj.ServerApplication;
import uni.proj.model.Server;

import uni.proj.Config;

void main() {
    if(Config.NO_GUI) {
        Server server = new Server();
        server.startServer();
    } else {
        ServerApplication.launchApplication();
    }
}