# Initiative Tracker

This application helps tracking during encounters which character is next.
It implements encounter rules of Pathfinder 2 and supports
- reordering when a character dies
- delaying turns
- player characters come after non-player characters

This project is a Compose Multiplatform Projects, which builds an Android application,
a web browser version and an iOS application (the iOS version is currently not tested though).

It also allows multiple devices to connect to each other (when in the same local network) to show
the same encounter and each device to interact with it, but that is not yet working reliably.

We have some plans to add more advanced conflict resolution features by using 
conflict-free replicated data types.