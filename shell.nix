{ pkgs ? import <nixpkgs> (import ./config.nix) }:

let
  bin-path = pkgs.lib.makeBinPath [
    # used in ./gradlew script
    pkgs.coreutils
    pkgs.findutils
    pkgs.gnused

    # used by some gradle tasks
    pkgs.nodejs
  ];

  # needed for desktop target (via downloaded skiko)
  ld-library-path = pkgs.lib.makeLibraryPath [
    pkgs.libGL
  ];

  # needed for android target
  android = pkgs.androidenv.composeAndroidPackages {
    toolsVersion = null;
    includeEmulator = false;
    platformVersions = [ "34" ];
    includeSources = false;
    includeSystemImages = false;
    systemImageTypes = [];
    abiVersions = [];
    cmakeVersions = [];
    includeNDK = false;
    ndkVersions = [];
    useGoogleAPIs = false;
    useGoogleTVAddOns = false;
    includeExtras = [];
  };
  inherit (android) androidsdk;

  gradle = pkgs.writeShellApplication {
    name = "gradle";
    text = ''
      if [[ ! -f "./gradlew" ]]; then
        echo "Cannot find gradle wrapper (./gradlew)" >&1
        exit 1
      fi

      PATH=${bin-path} \
      LD_LIBRARY_PATH=${ld-library-path} \
      JAVA_HOME=${pkgs.jre} \
      ANDROID_HOME=${androidsdk}/libexec/android-sdk \
      exec "./gradlew" -PnixManaged=true "$@"
    '';
  };
in

pkgs.mkShell {
  packages = [
    gradle
  ];
}
