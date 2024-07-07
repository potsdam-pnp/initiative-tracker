{ pkgs ? import <nixpkgs> {} }:

let
  gradle = pkgs.buildFHSEnv {
    name = "gradle";
    targetPkgs = pkgs: [
      pkgs.jdk
    ];
    runScript = "./gradlew";
  };
in

pkgs.mkShell {
  packages = [
    gradle
  ];
}