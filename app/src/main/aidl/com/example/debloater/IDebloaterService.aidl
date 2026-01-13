package com.dev.debloater;

interface IDebloaterService {
    void uninstall(String packageName);
    void disable(String packageName);
    void destroy();
}
