{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        config = import ./config.nix // {
          inherit system;
        };
        pkgs = import nixpkgs config;
      in {
        devShells.default = import ./shell.nix { inherit pkgs; };
      }
    );
}
