package io.immutables.jaeger;
public abstract class View {
    private final String templatePath;

    protected View(String templatePath) {
        this.templatePath = templatePath;
    }
}