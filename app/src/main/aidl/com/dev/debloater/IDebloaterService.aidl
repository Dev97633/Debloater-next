package com.dev.debloater;

interface IDebloaterService {
    void uninstall(String packageName);
    void restore(String packageName);
    void disable(String packageName);
    void enable(String packageName);
    void destroy();
}
