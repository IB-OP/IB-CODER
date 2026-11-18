package com.example.device;

interface IDeviceShellService {
    int getUid();
    String execCommand(String command, int timeoutSeconds);
    void destroy();
}
