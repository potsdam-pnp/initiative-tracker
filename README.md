# Initiative Tracker

This application helps tracking during encounters which character is next.
It implements encounter rules of Pathfinder 2 and supports
- reordering when a character dies
- delaying turns
- player characters come after non-player characters

This project is a Compose Multiplatform Projects, which builds an Android application and
a web browser version.

It also allows multiple devices to connect to each other (when in the same local network) to show
the same encounter and each device to interact with it. Conflict-free replicated data types are
used to handle conflict resolution between different states on different devices.
