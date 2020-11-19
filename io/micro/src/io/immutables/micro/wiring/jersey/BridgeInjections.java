package io.immutables.micro.wiring.jersey;

import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.Priority;
import javax.ws.rs.core.GenericType;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.glassfish.jersey.inject.hk2.Hk2InjectionManagerFactory;
import org.glassfish.jersey.internal.inject.Bindings;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.InjectionManagerFactory;

/**
 * Tried to use Guice/HK2 bridge - didn't worked, lifecycle issues etc. This is minimal and so far quite correct way of
 * one directional guice bridge with binding early initialized.
 */
@Priority(12) // to override HK2 and use it as a delegate
public class BridgeInjections implements InjectionManagerFactory {
  private final Hk2InjectionManagerFactory factory = new Hk2InjectionManagerFactory();
  public static final ThreadLocal<Injector> injector = new ThreadLocal<>();

  @Override
  public InjectionManager create(Object parent) {
    InjectionManager manager = factory.create(parent);
    @Nullable Injector injector = BridgeInjections.injector.get();

    for (; injector != null; injector = injector.getParent()) {
      for (Map.Entry<Key<?>, com.google.inject.Binding<?>> entry : injector.getBindings().entrySet()) {
        Key<?> key = entry.getKey();
        var hk2Binding = Bindings.supplier(entry.getValue().getProvider()::get);
        if (key.getAnnotation() != null) {
          hk2Binding.qualifiedBy(key.getAnnotation());
        }
        hk2Binding.to(new GenericType<>(key.getTypeLiteral().getType()));

        manager.register(hk2Binding);
      }
    }
    return manager;
  }
}
