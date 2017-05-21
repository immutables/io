package io.immutables.jaeger;

import org.immutables.value.Value;

@Value.Immutable
public abstract class MyView extends View {

    private static final String TEMPLATE_PATH = "/my/view.mustache";

    public MyView() {
        super(TEMPLATE_PATH);
    }

    @Value.Parameter
    public abstract String data();
}