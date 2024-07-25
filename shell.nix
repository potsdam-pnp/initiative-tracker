{ pkgs ? import <nixpkgs> {} }:

let
  # needed for headless browser testing
  chrome-bin = "${pkgs.chromium}/bin/chromium";

  # used in ./gradlew script
  bin-path = pkgs.lib.makeBinPath [
    pkgs.coreutils
    pkgs.findutils
    pkgs.gnused
  ];

  # needed for desktop target (via downloaded skiko)
  ld-library-path = pkgs.lib.makeLibraryPath [
    pkgs.libGL
  ];

  # needed for wasm target (via downloaded node)
  # (requires https://github.com/Mic92/nix-ld)
  nix-ld = "${pkgs.stdenv.cc}/nix-support/dynamic-linker";
  nix-ld-library-path = pkgs.lib.makeLibraryPath [
    pkgs.stdenv.cc.cc.lib
  ];

  gradle = pkgs.writeShellApplication {
    name = "gradle";
    text = ''
      if [[ ! -f "./gradlew" ]]; then
        echo "Cannot find gradle wrapper (./gradlew)" >&1
        exit 1
      fi

      CHROME_BIN=${chrome-bin} \
      PATH=${bin-path} \
      LD_LIBRARY_PATH=${ld-library-path} \
      NIX_LD_LIBRARY_PATH=${nix-ld-library-path} \
      NIX_LD=$(cat "${nix-ld}") \
      JAVA_HOME=${pkgs.jre} \
      exec "./gradlew" "$@"
    '';
  };
in

pkgs.mkShell {
  packages = [
    gradle
  ];
}