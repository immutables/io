package io.immutables.micro.tester;

import io.immutables.micro.creek.Broker;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;

public class BrokerTestModule implements Module {

  @Override
  public void configure(Binder binder) {
    Multibinder.newSetBinder(binder, TesterFacets.When.class)
        .addBinding()
        .toInstance(new TesterFacets.When() {
          @Inject
          Broker broker;

          @Override
          public void afterEach() {
            broker.clear();
          }
        });
  }
}
