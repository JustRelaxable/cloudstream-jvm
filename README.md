
# Cloudstream JVM

This repository contains code for base cloudstream functionality without android dependencies. It includes 2 projects: Core and Webserver. 

## Core

Use Core for plugin management, repository management, file IO. Its code taken from original CS android app. It needs a lot of improvements so PRs are welcome.

Core assumes the *.cs3 plugin contains base.jar inside it. Right now the gradle plugin of CS don't generate plugins like that. Please see my forked implementation [cloudstream-extensions-gradle-plugin](https://github.com/JustRelaxable/cloudstream-extensions-gradle-plugin).

## Webserver

Use Webserver for exposing Core functionality as well as proxy. Build your app on top of it. Again PRs are welcome.


## Contributing

Contributions are always welcome! This project needs a lot of testing stuff. You can also checkout [cloudstream-react](https://github.com/JustRelaxable/cloudstream-react) project if you want to contribute front-end.

