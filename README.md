# Clojbot

This project contains a bare-minimum IRC bot written in Clojure. The main idea of the bot is to be as resilient as possible. But above all, it should be clean code and maintainable (i.e., comments are very welcome).

## Configuration
The repository has a folder `conf/` that contains a few configuration files. Each of them belongs to a module. The `server.edn` file is for the bot and must be configured. All files should be self-explanatory with the samples that are provided.

## Usage

`lein run`. Be sure to configure the bot properly in `conf/servers.end`. You can also create an uberjar and run that.

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
