package io.immutables;

import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;

@Nonnull(when = When.MAYBE)
@TypeQualifierNickname
public @interface nullable {}
