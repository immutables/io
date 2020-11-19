package io.immutables.micro;

import com.google.inject.BindingAnnotation;
import com.google.inject.Key;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import org.junit.BeforeClass;
import org.junit.Test;
import static com.google.common.collect.ImmutableMap.of;
import static io.immutables.that.Assert.that;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static io.immutables.micro.References.reference;

@ReferencesTest.Info
@Secured(value = "A", count = 2, type = ElementType.FIELD)
public class ReferencesTest {
  private final Secured secured = getClass().getAnnotation(Secured.class);
  private final Info info = getClass().getAnnotation(Info.class);

  @BeforeClass
  public static void setUp() {
    References.refToType.putAll(of(
        "secured", "io.immutables.micro.Secured",
        "info", "io.immutables.micro.ReferencesTest$Info"));
  }

  @Test
  public void keyToReference() {
    that(References.reference(Key.get(Integer.class)).value()).is("java.lang.Integer");
    that(reference(Key.get(Integer.class, Secured.class)).value()).is("java.lang.Integer;qualifier=secured");
    that(reference(Key.get(Integer.class, secured)).value())
        .is("java.lang.Integer;qualifier=secured;count=2;type=FIELD;value=A");
    that(References.reference(Key.get(Integer.class, Info.class)).value()).is("java.lang.Integer;qualifier=info");
    that(References.reference(Key.get(Integer.class, info)).value()).is("java.lang.Integer;qualifier=info");
  }

  @Test(expected = IllegalArgumentException.class)
  public void nonCanonicalTypeNotAllowed() {
    References.reference(((Runnable) () -> {}).getClass());
    that().unreachable();
  }

  @Test(expected = IllegalArgumentException.class)
  public void annotationIsNotRegistered() {
    References.reference(Integer.class, NotRegistered.class);
    that().unreachable();
  }

  @Test
  public void classToReference() {
    that(References.reference(Integer.class).value()).is("java.lang.Integer");
    that(reference(Integer.class, Secured.class).value()).is("java.lang.Integer;qualifier=secured");
    that(reference(Integer.class, secured).value())
        .is("java.lang.Integer;qualifier=secured;count=2;type=FIELD;value=A");
    that(References.reference(Integer.class, Info.class).value()).is("java.lang.Integer;qualifier=info");
    that(References.reference(Integer.class, info).value()).is("java.lang.Integer;qualifier=info");
  }

  @Test
  public void referenceToKeyWithAnnotationNoAttributes() {
    Key<Integer> k1 = Key.get(Integer.class, Info.class);
    that(k1).equalTo(Key.get(Integer.class, info));

    Key<Integer> k2 = References.key(Manifest.Reference.of("java.lang.Integer;qualifier=info"));

    that((Object) k2).equalTo(k1);
    that((Object) k2.getAnnotationType()).equalTo(Info.class);
    that(k2.getAnnotation()).isNull();
  }

  @Test
  public void referenceToKeyWithAnnotationWithAttributes() {
    Key<Integer> k1 = Key.get(Integer.class, secured);
    that(k1).notEqual(Key.get(Integer.class, Secured.class));

    Key<Integer> k2 = References.key(Manifest.Reference.of("java.lang.Integer;qualifier=secured;count=2;type=FIELD;value=A"));

    that((Object) k2).equalTo(k1);
    that((Object) k2.getAnnotationType()).equalTo(Secured.class);
    that(k1.getAnnotation()).equalTo(secured);
  }

  class InnerClass {}

  @Retention(RUNTIME)
  @BindingAnnotation
  public @interface Info {}

  @Retention(RUNTIME)
  @BindingAnnotation
  public @interface NotRegistered {}
}
