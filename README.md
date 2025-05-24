# Initiative Tracker

![F-Droid Version](https://img.shields.io/f-droid/v/io.github.potsdam_pnp.initiative_tracker)
![GitHub Release](https://img.shields.io/github/v/release/potsdam-pnp/initiative-tracker)

[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/io.github.potsdam_pnp.initiative_tracker)

This application helps track whose turn is next during encounters. 
It implements the encounter rules of Pathfinder 2 and supports the following features:
- Reordering when a character dies
- Delaying turns
- Ensuring player characters take their turn after non-player characters when they have the same initiative

This project is a Compose Multiplatform Projects, which builds an Android application,
a web browser version as well as a desktop version.

It also allows multiple devices to connect to each other (when in the same local network) to show
the same encounter and each device to interact with it. Conflict-free replicated data types are
used to handle conflict resolution between different states on different devices.
