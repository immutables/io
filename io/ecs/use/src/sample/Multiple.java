package sample;

import a.ecs.KeyValServicelet;
import location.ecs.CoordinateServicelet;
import location.ecs.SorrowsServicelet;
import io.immutables.micro.wiring.MainLauncher;

class Multiple {
  public static void main(String[] args) {
    new MainLauncher(args).use(
        KeyValServicelet.KEY_VAL,
        SorrowsServicelet.SORROWS,
        CoordinateServicelet.COORDINATE
    ).run();
  }
}
